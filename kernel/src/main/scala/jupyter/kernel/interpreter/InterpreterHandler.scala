package jupyter
package kernel
package interpreter

import MessageSocket.Channel
import com.typesafe.scalalogging.slf4j.LazyLogging
import protocol._, Formats._, Output.{LanguageInfo, ConnectReply}

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }, Shapeless._

import scalaz.{\/-, -\/}

object InterpreterHandler extends LazyLogging {
  private def sendOk(send: (Channel, Message) => Unit, msg: ParsedMessage[_], executionCount: Int): Message =
    msg.reply(
      "execute_reply",
      Output.ExecuteOkReply(
        execution_count=executionCount,
        payload=Nil,
        user_expressions=Map.empty
      )
    )

  private def sendError(send: (Channel, Message) => Unit, msg: ParsedMessage[_], executionCount: Int, error: String): Message =
    sendError(send, msg, Output.Error(executionCount, "", "", error.split("\n").toList))

  private def sendError(send: (Channel, Message) => Unit, msg: ParsedMessage[_], err: Output.Error): Message = {
    send(
      Channel.Publish,
      msg.pub(
        "error",
        err
      )
    )

    msg.reply(
      "execute_reply",
      Output.ExecuteErrorReply(
        execution_count=err.execution_count,
        ename=err.ename,
        evalue=err.evalue,
        traceback=err.traceback
      )
    )
  }

  private def sendAbort(send: (Channel, Message) => Unit, msg: ParsedMessage[_], executionCount: Int): Message =
    msg.reply(
      "execute_reply",
      Output.ExecuteAbortReply(
        execution_count=executionCount
      )
    )

  private def sendStream(send: (Channel, Message) => Unit, msg: ParsedMessage[_], name: String, data: String): Unit =
    send(Channel.Publish, msg.pub("stream", Output.Stream(name=name, text=data)))

  private def busy[T](send: (Channel, Message) => Unit, parentHeader: Option[Header])(block: => T): T = {
    sendStatus(send, parentHeader, ExecutionState.busy)

    try block
    finally {
      sendStatus(send, parentHeader, ExecutionState.idle)
    }
  }

  private def sendStatus(send: (Channel, Message) => Unit, parentHeader: Option[Header], state: ExecutionState): Unit =
    send(
      Channel.Publish,
      Message(ParsedMessage(
        "status" :: Nil,
        Header(msg_id=NbUUID.randomUUID(),
          username="scala_kernel",
          session=NbUUID.randomUUID(),
          msg_type="status",
          version = Protocol.versionStrOpt
        ),
        parentHeader,
        Map.empty,
        Output.Status(
          execution_state=state)
      ))
    )

  private def execute(send: (Channel, Message) => Unit, interpreter: Interpreter, msg: ParsedMessage[Input.ExecuteRequest]): Message = {
    val content = msg.content
    val code = content.code
    val silent = content.silent || code.trim.endsWith(";")

    if (code.trim.isEmpty)
      sendOk(send, msg, interpreter.executionCount)
    else {
      send(
        Channel.Publish,
        msg.pub(
          "execute_input",
          Output.ExecuteInput(
            execution_count = interpreter.executionCount + 1,
            code = code
          )
        )
      )

      busy(send, Some(msg.header)) {
        def stream(name: String): String => Unit =
          if (silent)
            _ => ()
          else
            sendStream(send, msg, name, _)

        val toStdout = stream("stdout")
        val toStderr = stream("stderr")

        interpreter.interpret(code, Some(toStdout, toStderr), content.store_history getOrElse !silent) match {
          case Interpreter.Value(repr) if !silent =>
            send(
              Channel.Publish,
              msg.pub(
                "execute_result",
                Output.ExecuteResult(
                  execution_count = interpreter.executionCount,
                  data = repr.data.toMap
                )
              )
            )

            sendOk(send, msg, interpreter.executionCount)

          case _: Interpreter.Value if silent =>
            sendOk(send, msg, interpreter.executionCount)

          case Interpreter.NoValue =>
            sendOk(send, msg, interpreter.executionCount)

          case exc @ Interpreter.Exception(name, message, _, _) =>
            sendError(send, msg, Output.Error(interpreter.executionCount, name, message, exc.traceBack))

          case Interpreter.Error(errorMsg) =>
            sendError(send, msg, interpreter.executionCount, errorMsg)

          case Interpreter.Incomplete =>
            sendError(send, msg, interpreter.executionCount, "incomplete")

          case Interpreter.Cancelled =>
            sendAbort(send, msg, interpreter.executionCount)
        }
      }
    }
  }

  private def commonPrefix(xs: List[String]): String =
    if (xs.isEmpty || xs.contains(""))
      ""
    else if (xs.tail.forall(_.head == xs.head.head))
      "" + xs.head.head + commonPrefix(xs.map(_.tail))
    else
      ""

  /** Find longest string that is a suffix of `head` and prefix of `tail`.
    *
    *  Example:
    *
    *    isInstance
    *  x.is
    *    ^^
    *
    *  >>> Util.suffixPrefix("x.is", "isInstance")
    *  "is"
    */
  private def suffixPrefix(head: String, tail: String): String = {
    def helper(p: String): String =
      if (tail startsWith p)
        p
      else
        helper(p drop 1)

    helper(head)
  }

  private def complete(send: (Channel, Message) => Unit, interpreter: Interpreter, msg: ParsedMessage[Input.CompleteRequest]): Message = {
    val code =
      if (msg.content.cursor_pos >= 0)
        msg.content.code.splitAt(msg.content.cursor_pos)._1.split("""[^\w.%]""").last
      else
        msg.content.code

    val (matches, suffixLen) = {
      val _completions = interpreter.complete(code)
      val common = commonPrefix(_completions)
      val prefix = suffixPrefix(code, common)
      (_completions.map(code + _.stripPrefix(prefix)), code.length)
    }

    msg.reply(
      "complete_reply",
      Output.CompleteReply(
        matches=matches,
        cursor_start = (if (msg.content.cursor_pos >= 0) msg.content.cursor_pos else code.length) - suffixLen,
        cursor_end = if (msg.content.cursor_pos >= 0) msg.content.cursor_pos else code.length,
        status=ExecutionStatus.ok
      )
    )
  }

  private def kernelInfo(send: (Channel, Message) => Unit, msg: ParsedMessage[Input.KernelInfoRequest]): Message =
    msg.reply(
      "kernel_info_reply",
      Output.KernelInfoReply(
        protocol_version = s"${Protocol.version._1}.${Protocol.version._2}",
        language_info = LanguageInfo(
          name="scala",
          codemirror_mode = "text/x-scala",
          file_extension = "scala",
          mimetype = "text/x-scala"
        )
      )
    )

  private def connect(send: (Channel, Message) => Unit, connectReply: ConnectReply, msg: ParsedMessage[Input.ConnectRequest]): Message =
    msg.reply(
      "connect_reply",
      connectReply
    )

  private def shutdown(send: (Channel, Message) => Unit, msg: ParsedMessage[Input.ShutdownRequest]): Message = {
    send(
      Channel.Requests,
      msg.reply(
        "shutdown_reply",
        Output.ShutdownReply(restart=msg.content.restart)
      )
    )
    Console.err println s"Shutting down kernel"
    sys.exit() // Maybe handle that with a callback
  }

  private def objectInfo(send: (Channel, Message) => Unit, msg: ParsedMessage[Input.ObjectInfoRequest]): Message =
    msg.reply(
      "object_info_reply",
      Output.ObjectInfoNotFoundReply(name=msg.content.oname)
    )

  private def history(send: (Channel, Message) => Unit, msg: ParsedMessage[Input.HistoryRequest]): Message =
    msg.reply(
      "history_reply",
      Output.HistoryReply(history=Nil)
    )

  private def commOpen(msg: ParsedMessage[InputOutput.CommOpen]): Unit =
    println(msg)

  private def commMsg(msg: ParsedMessage[InputOutput.CommMsg]): Unit =
    println(msg)

  private def commClose(msg: ParsedMessage[InputOutput.CommClose]): Unit =
    println(msg)

  def apply(
    send: (Channel, Message) => Unit,
    connectReply: ConnectReply,
    interpreter: Interpreter
  ): Message => Unit = { rawMsg =>
    rawMsg.decode match {
      case -\/(err) =>
        logger error s"Decoding message: $err"

      case \/-(msg) =>
        val reply: Message =
          (msg.header.msg_type, msg.content) match {
            case ("execute_request", r: Input.ExecuteRequest) =>
              execute(send, interpreter, msg.copy(content = r))
            case ("complete_request", r: Input.CompleteRequest) =>
              complete(send, interpreter, msg.copy(content = r))
            case ("kernel_info_request", r: Input.KernelInfoRequest) =>
              kernelInfo(send, msg.copy(content = r))
            case ("object_info_request", r: Input.ObjectInfoRequest) =>
              objectInfo(send, msg.copy(content = r))
            case ("connect_request", r: Input.ConnectRequest) =>
              connect(send, connectReply: ConnectReply, msg.copy(content = r))
            case ("shutdown_request", r: Input.ShutdownRequest) =>
              shutdown(send, msg.copy(content = r))
            case ("history_request", r: Input.HistoryRequest) =>
              history(send, msg.copy(content = r))

            // FIXME These are not handled well
            case ("comm_open", r: InputOutput.CommOpen) =>
              commOpen(msg.copy(content = r))
              msg.reply("bad_request", Json.obj()) // ???
            case ("comm_msg", r: InputOutput.CommMsg) =>
              commMsg(msg.copy(content = r))
              msg.reply("bad_request", Json.obj()) // ???
            case ("comm_close", r: InputOutput.CommClose) =>
              commClose(msg.copy(content = r))
              msg.reply("bad_request", Json.obj()) // ???

            case _ =>
              logger debug s"Unrecognized message: $msg ($rawMsg)"
              msg.reply("bad_request", Json.obj())
          }

        send(Channel.Requests, reply)
    }
  }
}
