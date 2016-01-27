package jupyter
package kernel
package interpreter

import java.util.UUID

import jupyter.api._
import protocol._, Formats._, Output.{ LanguageInfo, ConnectReply }

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{\/, \/-, -\/}

object InterpreterHandler {
  private def ok(msg: ParsedMessage[_], executionCount: Int): Message =
    msg.reply("execute_reply", Output.ExecuteOkReply(execution_count = executionCount))

  private def abort(msg: ParsedMessage[_], executionCount: Int): Message =
    msg.reply("execute_reply", Output.ExecuteAbortReply(execution_count = executionCount))

  private def status(parentHeader: Option[Header], state: ExecutionState) =
    ParsedMessage(
      "status".getBytes("UTF-8") :: Nil,
      Header(
        msg_id = UUID.randomUUID().toString,
        username = parentHeader.fold("")(_.username),
        session = parentHeader.fold(UUID.randomUUID().toString)(_.session),
        msg_type = "status",
        version = Protocol.versionStrOpt
      ),
      parentHeader,
      Map.empty,
      Output.Status(execution_state = state)
    ).toMessage

  private def busy(msg: ParsedMessage[_])(f: => Process[Task, (Channel, Message)]): Process[Task, (Channel, Message)] = {
    val start =
      Process.emit(
        Channel.Publish -> status(Some(msg.header), ExecutionState.busy)
      )

    val end =
      Process.emit(
        Channel.Publish -> status(Some(msg.header), ExecutionState.idle)
      )

    start ++ f ++ end
  }

  private def publishing(msg: ParsedMessage[_])(f: (Message => Unit) => Seq[Message]): Process[Task, (Channel, Message)] = {
    busy(msg) {
      val q = scalaz.stream.async.boundedQueue[Message](1000)

      val res = Task.unsafeStart {
        try f(q.enqueueOne(_).run)
        finally q.close.run
      }

      q.dequeue.map(Channel.Publish.->) ++ Process.eval(res).flatMap(l => Process.emitAll(l.map(Channel.Requests.->)))
    }
  }

  private def execute(interpreter: Interpreter, msg: ParsedMessage[Input.ExecuteRequest]): Process[Task, String \/ (Channel, Message)] = {
    val content = msg.content
    val code = content.code
    val silent = content.silent

    if (code.trim.isEmpty)
      Process.emit(\/-(Channel.Requests -> ok(msg, interpreter.executionCount)))
    else {
      val start =
        Process.emitAll(Seq(
          \/-(Channel.Publish -> msg.pub(
            "execute_input",
            Output.ExecuteInput(
              execution_count = interpreter.executionCount + 1,
              code = code
            )
          ))
        ))

      start ++ publishing(msg) { pub =>
        def error(msg: ParsedMessage[_], err: Output.Error): Message = {
          pub(msg.pub("error", err))

          msg.reply(
            "execute_reply",
            Output.ExecuteErrorReply(
              execution_count = err.execution_count,
              ename = err.ename,
              evalue = err.evalue,
              traceback = err.traceback
            )
          )
        }

        def _error(msg: ParsedMessage[_], executionCount: Int, err: String): Message =
          error(msg, Output.Error(executionCount, "", "", err.split("\n").toList))

        Seq(interpreter.interpret(
          code,
          if (silent)
            Some(_ => (), _ => ())
          else
            Some(
              s => pub(msg.pub("stream", Output.Stream(name = "stdout", text = s))),
              s => pub(msg.pub("stream", Output.Stream(name = "stderr", text = s)))
            ),
          content.store_history getOrElse !silent,
          Some(msg)
        ) match {
          case Interpreter.Value(repr) if !silent =>
            pub(
              if (interpreter.resultDisplay)
                msg.pub(
                  "display_data",
                  Output.DisplayData(
                    source = "interpreter",
                    data = repr.data.toMap,
                    metadata = Map.empty
                  )
                )
              else
                msg.pub(
                  "execute_result",
                  Output.ExecuteResult(
                    execution_count = interpreter.executionCount,
                    data = repr.data.toMap
                  )
                )
            )

            ok(msg, interpreter.executionCount)

          case _: Interpreter.Value if silent =>
            ok(msg, interpreter.executionCount)

          case Interpreter.NoValue =>
            ok(msg, interpreter.executionCount)

          case exc@Interpreter.Exception(name, message, _, _) =>
            error(msg, Output.Error(interpreter.executionCount, name, message, exc.traceBack))

          case Interpreter.Error(errorMsg) =>
            _error(msg, interpreter.executionCount, errorMsg)

          case Interpreter.Incomplete =>
            _error(msg, interpreter.executionCount, "incomplete")

          case Interpreter.Cancelled =>
            abort(msg, interpreter.executionCount)
        })
      } .map(\/-(_))
    }
  }

  private def complete(interpreter: Interpreter, msg: ParsedMessage[Input.CompleteRequest]): Message = {
    val pos = Some(msg.content.cursor_pos).filter(_ >= 0) getOrElse msg.content.code.length
    val (i, matches) = interpreter.complete(msg.content.code, pos)

    msg.reply(
      "complete_reply",
      Output.CompleteReply(
        matches = matches.toList,
        cursor_start = i,
        cursor_end = pos,
        status = ExecutionStatus.ok
      )
    )
  }

  private def kernelInfo(implementation: (String, String), banner: String, languageInfo: LanguageInfo, msg: ParsedMessage[Input.KernelInfoRequest]): Message =
    msg.reply(
      "kernel_info_reply",
      Output.KernelInfoReply(
        protocol_version = s"${Protocol.version._1}.${Protocol.version._2}",
        implementation = implementation._1,
        implementation_version = implementation._2,
        language_info = languageInfo,
        banner = banner
      )
    )

  private def connect(connectReply: ConnectReply, msg: ParsedMessage[Input.ConnectRequest]): Message =
    msg.reply(
      "connect_reply",
      connectReply
    )

  private def shutdown(msg: ParsedMessage[Input.ShutdownRequest]): Message =
    msg.reply(
      "shutdown_reply",
      Output.ShutdownReply(restart=msg.content.restart)
    )

  private def objectInfo(msg: ParsedMessage[Input.ObjectInfoRequest]): Message =
    msg.reply(
      "object_info_reply",
      Output.ObjectInfoNotFoundReply(name=msg.content.oname)
    )

  private def history(msg: ParsedMessage[Input.HistoryRequest]): Message =
    msg.reply(
      "history_reply",
      Output.HistoryReply(history=Nil)
    )

  private def single(m: Message) = Process.emit(\/-(Channel.Requests -> m))

  def apply(interpreter: Interpreter,
            connectReply: ConnectReply,
            commHandler: (String, CommChannelMessage) => Unit,
            msg: Message): Process[Task, String \/ (Channel, Message)] =
    msg.decode match {
      case -\/(err) =>
        Process.emit(-\/(s"Decoding message: $err"))

      case \/-(parsedMessage) =>
        (parsedMessage.header.msg_type, parsedMessage.content) match {
          case ("connect_request", r: Input.ConnectRequest) =>
            single(connect(connectReply: ConnectReply, parsedMessage.copy(content = r)))
          case ("kernel_info_request", r: Input.KernelInfoRequest) =>
            single(kernelInfo(interpreter.implementation, interpreter.banner, interpreter.languageInfo, parsedMessage.copy(content = r))) ++ {
              if (interpreter.initialized)
                Process.empty
              else
                busy(parsedMessage) { interpreter.init(); Process.empty } .map(\/-(_))
            }

          case ("execute_request", r: Input.ExecuteRequest) =>
            execute(interpreter, parsedMessage.copy(content = r))
          case ("complete_request", r: Input.CompleteRequest) =>
            single(complete(interpreter, parsedMessage.copy(content = r)))
          case ("object_info_request", r: Input.ObjectInfoRequest) =>
            single(objectInfo(parsedMessage.copy(content = r)))

          case ("shutdown_request", r: Input.ShutdownRequest) =>
            // FIXME Propagate shutdown request
            single(shutdown(parsedMessage.copy(content = r)))

          case ("history_request", r: Input.HistoryRequest) =>
            single(history(parsedMessage.copy(content = r)))

          // FIXME These are not handled well
          case ("comm_open", r: InputOutput.CommOpen) =>
            // FIXME IPython messaging spec says: if target_name is empty, we should immediately reply with comm_close
            commHandler(r.comm_id, CommOpen(r.target_name, r.data.spaces2))
            Process.halt
          case ("comm_msg", r: InputOutput.CommMsg) =>
            commHandler(r.comm_id, CommMessage(r.data.spaces2))
            Process.halt
          case ("comm_close", r: InputOutput.CommClose) =>
            commHandler(r.comm_id, CommClose(r.data.spaces2))
            Process.halt

          case _ =>
            Process.emit(-\/(s"Unrecognized message: $parsedMessage ($msg)"))
        }
    }
}
