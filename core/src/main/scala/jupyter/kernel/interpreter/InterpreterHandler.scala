package jupyter
package kernel
package interpreter

import java.util.UUID
import java.util.concurrent.ExecutorService

import jupyter.api._
import jupyter.kernel.protocol._, Formats._

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }
import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal
import scalaz.concurrent.{ Strategy, Task }
import scalaz.stream.Process
import scalaz.{ -\/, \/ }

object InterpreterHandler extends LazyLogging {

  private def busy(msg: ParsedMessage[_])(f: => Process[Task, (Channel, Message)]): Process[Task, (Channel, Message)] = {

    def status(state: Publish.ExecutionState0) = {
      val statusMsg = ParsedMessage(
        List("status".getBytes("UTF-8")),
        Header(
          msg_id = UUID.randomUUID().toString,
          username = msg.header.username,
          session = msg.header.session,
          msg_type = "status",
          version = Protocol.versionStrOpt
        ),
        Some(msg.header),
        Map.empty,
        Publish.Status(execution_state = state)
      ).toMessage

      Process.emit(
        Channel.Publish -> statusMsg
      )
    }

    status(Publish.ExecutionState0.Busy) ++ f ++ status(Publish.ExecutionState0.Idle)
  }

  private def publishing(
    msg: ParsedMessage[_]
  )(
    f: (Message => Unit) => Seq[Message]
  )(implicit
    pool: ExecutorService
  ): Process[Task, (Channel, Message)] = {

    implicit val strategy = Strategy.Executor

    busy(msg) {
      val q = scalaz.stream.async.boundedQueue[Message](1000)

      val res = Task.unsafeStart {
        try f(q.enqueueOne(_).unsafePerformSync)
        finally q.close.unsafePerformSync
      }

      q.dequeue.map(Channel.Publish.->) ++ Process.eval(res).flatMap(l => Process.emitAll(l.map(Channel.Requests.->)))
    }
  }

  private def execute(
    interpreter: Interpreter,
    msg: ParsedMessage[ShellRequest.Execute]
  )(implicit
    pool: ExecutorService
  ): Process[Task, (Channel, Message)] = {

    def ok(msg: ParsedMessage[_], executionCount: Int): Message =
      msg.reply("execute_reply", ShellReply.Execute(executionCount, Map.empty))

    val content = msg.content
    val code = content.code
    val silent = content.silent.exists(x => x)

    if (code.trim.isEmpty)
      Process.emit(Channel.Requests -> ok(msg, interpreter.executionCount))
    else {
      val start = Process.emitAll(Seq(
        Channel.Publish -> msg.publish(
          "execute_input",
          Publish.ExecuteInput(
            execution_count = interpreter.executionCount + 1,
            code = code
          )
        )
      ))

      start ++ publishing(msg) { pub =>
        def error(msg: ParsedMessage[_], executionCount: Int, err: ShellReply.Error): Message = {
          pub(msg.publish("error", err))

          msg.reply(
            "execute_reply",
            ShellReply.Error(
              ename = err.ename,
              evalue = err.evalue,
              traceback = err.traceback
            )
          )
        }

        def _error(msg: ParsedMessage[_], executionCount: Int, err: String): Message =
          error(msg, executionCount, ShellReply.Error("", "", err.split("\n").toList))

        Seq(interpreter.interpret(
          code,
          if (silent)
            Some(_ => (), _ => ())
          else
            Some(
              s => pub(msg.publish("stream", Publish.Stream(name = "stdout", text = s), ident = "stdout")),
              s => pub(msg.publish("stream", Publish.Stream(name = "stderr", text = s), ident = "stderr"))
            ),
          content.store_history getOrElse !silent,
          Some(msg)
        ) match {
          case value: Interpreter.Value if !silent =>
            pub(
              if (interpreter.resultDisplay)
                msg.publish(
                  "display_data",
                  Publish.DisplayData(
                    value.map.mapValues(Json.jString),
                    Map.empty
                  )
                )
              else
                msg.publish(
                  "execute_result",
                  Publish.ExecuteResult(
                    interpreter.executionCount,
                    value.map.mapValues(Json.jString),
                    Map.empty
                  )
                )
            )

            ok(msg, interpreter.executionCount)

          case _: Interpreter.Value if silent =>
            ok(msg, interpreter.executionCount)

          case Interpreter.NoValue =>
            ok(msg, interpreter.executionCount)

          case exc @ Interpreter.Exception(name, message, _) =>
            error(msg, interpreter.executionCount, ShellReply.Error(name, message, exc.traceBack))

          case Interpreter.Error(errorMsg) =>
            _error(msg, interpreter.executionCount, errorMsg)

          case Interpreter.Incomplete =>
            _error(msg, interpreter.executionCount, "incomplete")

          case Interpreter.Cancelled =>
            msg.reply("execute_reply", ShellReply.Abort())
        })
      }
    }
  }

  private def complete(
    interpreter: Interpreter,
    msg: ParsedMessage[ShellRequest.Complete]
  ): Message = {

    val pos =
      if (msg.content.cursor_pos >= 0)
        msg.content.cursor_pos
      else
        msg.content.code.length

    val (start, end, matches) = interpreter.complete(msg.content.code, pos)

    msg.reply(
      "complete_reply",
      ShellReply.Complete(
        matches.toList,
        start,
        end,
        Map.empty
      )
    )
  }

  private def kernelInfo(
    implementation: (String, String),
    banner: String,
    languageInfo: ShellReply.KernelInfo.LanguageInfo,
    msg: ParsedMessage[ShellRequest.KernelInfo.type]
  ): Message =
    msg.reply(
      "kernel_info_reply",
      ShellReply.KernelInfo(
        s"${Protocol.versionMajor}.${Protocol.versionMinor}",
        implementation._1,
        implementation._2,
        languageInfo,
        banner
      )
    )

  private def connect(connectReply: ShellReply.Connect, msg: ParsedMessage[ShellRequest.Connect.type]): Message =
    msg.reply(
      "connect_reply",
      connectReply
    )

  private def shutdown(msg: ParsedMessage[ShellRequest.Shutdown]): Message =
    msg.reply(
      "shutdown_reply",
      ShellReply.Shutdown(restart = msg.content.restart)
    )

  private def inspect(msg: ParsedMessage[ShellRequest.Inspect]): Message =
    msg.reply(
      "object_info_reply",
      ShellReply.Inspect(found = false, Map.empty, Map.empty)
    )

  private def history(msg: ParsedMessage[ShellRequest.History]): Message =
    msg.reply(
      "history_reply",
      ShellReply.History.Default(Nil)
    )

  private def single(m: Message) = Process.emit(Channel.Requests -> m)


  def apply(
    interpreter: Interpreter,
    connectReply: ShellReply.Connect,
    commHandler: (String, CommChannelMessage) => Unit,
    msg: Message
  )(implicit
    pool: ExecutorService
  ): String \/ Process[Task, (Channel, Message)] = try {

    msg.msgType.flatMap {
      case "connect_request" =>
        msg.as[ShellRequest.Connect.type] { parsedMessage =>
          single(connect(connectReply, parsedMessage))
        }

      case "kernel_info_request" =>
        msg.as[ShellRequest.KernelInfo.type] { parsedMessage =>
          single(kernelInfo(
            interpreter.implementation,
            interpreter.banner,
            interpreter.languageInfo,
            parsedMessage
          )) ++ {
            if (interpreter.initialized)
              Process.empty
            else
              busy(parsedMessage) { interpreter.init(); Process.empty }
          }
        }

      case "execute_request" =>
        msg.as[ShellRequest.Execute] { parsedMessage =>
          execute(interpreter, parsedMessage)
        }

      case "complete_request" =>
        msg.as[ShellRequest.Complete] { parsedMessage =>
          single(complete(interpreter, parsedMessage))
        }

      case "object_info_request" =>
        msg.as[ShellRequest.Inspect] { parsedMessage =>
          single(inspect(parsedMessage))
        }

      case "shutdown_request" =>
        msg.as[ShellRequest.Shutdown] { parsedMessage =>
          single(shutdown(parsedMessage))
        }

      case "history_request" =>
        msg.as[ShellRequest.History] { parsedMessage =>
          single(history(parsedMessage))
        }

      case "comm_open" =>
        msg.as[Comm.Open] { parsedMessage =>
          val r = parsedMessage.content
          commHandler(r.comm_id, CommOpen(r.target_name, r.data.spaces2))
          Process.halt
        }

      case "comm_msg" =>
        msg.as[Comm.Message] { parsedMessage =>
          val r = parsedMessage.content
          commHandler(r.comm_id, CommMessage(r.data.spaces2))
          Process.halt
        }

      case "comm_close" =>
        msg.as[Comm.Close] { parsedMessage =>
          val r = parsedMessage.content
          commHandler(r.comm_id, CommClose(r.data.spaces2))
          Process.halt
        }
    }
  } catch {
    case NonFatal(e) =>
      logger.error(s"Exception while handling message\n$msg", e)
      -\/(e.toString)
  }
}
