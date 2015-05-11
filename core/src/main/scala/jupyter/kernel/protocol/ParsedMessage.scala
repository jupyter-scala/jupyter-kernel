package jupyter
package kernel
package protocol

import _root_.scala.util.Try

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }

import scalaz.{\/-, -\/, \/}


object Formats {
  import Shapeless._

  implicit def d = DecodeJson.derive[InputOutput.CommOpen]
  implicit def d1 = DecodeJson.derive[InputOutput.CommMsg]
  implicit def d2 = DecodeJson.derive[InputOutput.CommClose]
  implicit def d3 = DecodeJson.derive[Output.ExecuteResult]
  implicit def d4 = DecodeJson.derive[Meta.MetaKernelStartReply]


  implicit def inputExecuteRequestDecodeJson = implicitly[DecodeJson[Input.ExecuteRequest]]
  implicit def inputCompleteRequestDecodeJson = implicitly[DecodeJson[Input.CompleteRequest]]
  implicit def inputKernelInfoRequestDecodeJson = implicitly[DecodeJson[Input.KernelInfoRequest]]
  implicit def inputObjectInfoRequestDecodeJson = implicitly[DecodeJson[Input.ObjectInfoRequest]]
  implicit def inputConnectRequestDecodeJson = implicitly[DecodeJson[Input.ConnectRequest]]
  implicit def inputShutdownRequestDecodeJson = implicitly[DecodeJson[Input.ShutdownRequest]]
  implicit def inputHistoryRequestDecodeJson = implicitly[DecodeJson[Input.HistoryRequest]]
  implicit def inputInputReplyDecodeJson = implicitly[DecodeJson[Input.InputReply]]
  implicit def outputExecuteOkReplyDecodeJson = implicitly[DecodeJson[Output.ExecuteOkReply]]

  implicit def outputExecuteErrorReplyDecodeJson = implicitly[DecodeJson[Output.ExecuteErrorReply]]
  implicit def outputExecuteAbortReplyDecodeJson = implicitly[DecodeJson[Output.ExecuteAbortReply]]
  implicit def outputObjectInfoNotFoundReplyDecodeJson = implicitly[DecodeJson[Output.ObjectInfoNotFoundReply]]
  implicit def outputObjectInfoFoundReplyDecodeJson = implicitly[DecodeJson[Output.ObjectInfoFoundReply]]
  implicit def outputCompleteReplyDecodeJson = implicitly[DecodeJson[Output.CompleteReply]]
  implicit def outputHistoryReplyDecodeJson = implicitly[DecodeJson[Output.HistoryReply]]
  implicit def outputConnectReplyDecodeJson = implicitly[DecodeJson[Output.ConnectReply]]
  implicit def outputKernelInfoReplyDecodeJson = implicitly[DecodeJson[Output.KernelInfoReply]]
  implicit def outputKernelInfoReplyV4DecodeJson = implicitly[DecodeJson[Output.KernelInfoReplyV4]]
  implicit def outputShutdownReplyDecodeJson = implicitly[DecodeJson[Output.ShutdownReply]]
  implicit def outputStreamDecodeJson = implicitly[DecodeJson[Output.Stream]]
  implicit def outputStreamV4DecodeJson = implicitly[DecodeJson[Output.StreamV4]]
  implicit def outputDisplayDataDecodeJson = implicitly[DecodeJson[Output.DisplayData]]
  implicit def outputExecuteInputDecodeJson = implicitly[DecodeJson[Output.ExecuteInput]]
  implicit def outputPyOutV3DecodeJson = implicitly[DecodeJson[Output.PyOutV3]]
  implicit def outputPyOutV4DecodeJson = implicitly[DecodeJson[Output.PyOutV4]]
  implicit def outputPyErrDecodeJson = implicitly[DecodeJson[Output.PyErr]]
  implicit def outputErrorDecodeJson = implicitly[DecodeJson[Output.Error]]
  implicit def outputStatusDecodeJson = implicitly[DecodeJson[Output.Status]]
  implicit def metaMetaKernelStartRequestDecodeJson = implicitly[DecodeJson[Meta.MetaKernelStartRequest]]


  implicit def inputExecuteRequestEncodeJson = implicitly[EncodeJson[Input.ExecuteRequest]]
  implicit def inputCompleteRequestEncodeJson = implicitly[EncodeJson[Input.CompleteRequest]]
  implicit def inputKernelInfoRequestEncodeJson = implicitly[EncodeJson[Input.KernelInfoRequest]]
  implicit def inputObjectInfoRequestEncodeJson = implicitly[EncodeJson[Input.ObjectInfoRequest]]
  implicit def inputConnectRequestEncodeJson = implicitly[EncodeJson[Input.ConnectRequest]]
  implicit def inputShutdownRequestEncodeJson = implicitly[EncodeJson[Input.ShutdownRequest]]
  implicit def inputHistoryRequestEncodeJson = implicitly[EncodeJson[Input.HistoryRequest]]
  implicit def inputInputReplyEncodeJson = implicitly[EncodeJson[Input.InputReply]]
  implicit def outputExecuteOkReplyEncodeJson = implicitly[EncodeJson[Output.ExecuteOkReply]]

  implicit def outputExecuteErrorReplyEncodeJson = implicitly[EncodeJson[Output.ExecuteErrorReply]]
  implicit def outputExecuteAbortReplyEncodeJson = implicitly[EncodeJson[Output.ExecuteAbortReply]]
  implicit def outputObjectInfoNotFoundReplyEncodeJson = implicitly[EncodeJson[Output.ObjectInfoNotFoundReply]]
  implicit def outputObjectInfoFoundReplyEncodeJson = implicitly[EncodeJson[Output.ObjectInfoFoundReply]]
  implicit def outputCompleteReplyEncodeJson = implicitly[EncodeJson[Output.CompleteReply]]
  implicit def outputHistoryReplyEncodeJson = implicitly[EncodeJson[Output.HistoryReply]]
  implicit def outputConnectReplyEncodeJson = implicitly[EncodeJson[Output.ConnectReply]]
  implicit def outputKernelInfoReplyEncodeJson = implicitly[EncodeJson[Output.KernelInfoReply]]
  implicit def outputKernelInfoReplyV4EncodeJson = implicitly[EncodeJson[Output.KernelInfoReplyV4]]
  implicit def outputShutdownReplyEncodeJson = implicitly[EncodeJson[Output.ShutdownReply]]
  implicit def outputStreamEncodeJson = implicitly[EncodeJson[Output.Stream]]
  implicit def outputStreamV4EncodeJson = implicitly[EncodeJson[Output.StreamV4]]
  implicit def outputDisplayDataEncodeJson = implicitly[EncodeJson[Output.DisplayData]]
  implicit def outputExecuteInputEncodeJson = implicitly[EncodeJson[Output.ExecuteInput]]
  implicit def outputPyOutV3EncodeJson = implicitly[EncodeJson[Output.PyOutV3]]
  implicit def outputPyOutV4EncodeJson = implicitly[EncodeJson[Output.PyOutV4]]
  implicit def outputExecuteResultEncodeJson = implicitly[EncodeJson[Output.ExecuteResult]]
  implicit def outputPyErrEncodeJson = implicitly[EncodeJson[Output.PyErr]]
  implicit def outputErrorEncodeJson = implicitly[EncodeJson[Output.Error]]
  implicit def outputStatusEncodeJson = implicitly[EncodeJson[Output.Status]]
  implicit def metaMetaKernelStartRequestEncodeJson = implicitly[EncodeJson[Meta.MetaKernelStartRequest]]
  implicit def metaMetaKernelStartReplyEncodeJson = implicitly[EncodeJson[Meta.MetaKernelStartReply]]

  implicit def headerDecodeJson = implicitly[DecodeJson[Header]]
  implicit def headerEncodeJson = implicitly[EncodeJson[Header]]
  implicit def headerV4DecodeJson = implicitly[DecodeJson[HeaderV4]]
  implicit def headerV4EncodeJson = implicitly[EncodeJson[HeaderV4]]

  implicit def connectionDecodeJson = implicitly[DecodeJson[Connection]]
  implicit def connectionEncodeJson = implicitly[EncodeJson[Connection]]

  implicit val decodeUUID = DecodeJson[NbUUID] { c =>
    StringDecodeJson.decode(c).flatMap { s =>
      NbUUID.fromString(s) match {
        case Some(uuid) =>
          DecodeResult.ok(uuid)
        case None =>
          DecodeResult.fail(s"Invalid UUID: $s", c.history)
      }
    }
  }

  implicit val encodeUUID = EncodeJson[NbUUID] { uuid =>
    Json.jString(uuid.toString)
  }

  implicit val encodeExecutionStatusOk: EncodeJson[ExecutionStatus.ok.type] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus.ok.type](_.toString)

  implicit val decodeExecutionStatusOk: DecodeJson[ExecutionStatus.ok.type] =
    DecodeJson[ExecutionStatus.ok.type] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "ok" => DecodeResult.ok(ExecutionStatus.ok)
        case _ => DecodeResult.fail("Expected ok status", c.history)
      }
    }

  implicit val encodeExecutionStatusError: EncodeJson[ExecutionStatus.error.type] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus.error.type](_.toString)

  implicit val decodeExecutionStatusError: DecodeJson[ExecutionStatus.error.type] =
    DecodeJson[ExecutionStatus.error.type] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "error" => DecodeResult.ok(ExecutionStatus.error)
        case _ => DecodeResult.fail("Expected error status", c.history)
      }
    }

  implicit val encodeExecutionStatusAbort: EncodeJson[ExecutionStatus.abort.type] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus.abort.type](_.toString)

  implicit val decodeExecutionStatusAbort: DecodeJson[ExecutionStatus.abort.type] =
    DecodeJson[ExecutionStatus.abort.type] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "abort" => DecodeResult.ok(ExecutionStatus.abort)
        case _ => DecodeResult.fail("Expected abort status", c.history)
      }
    }

  implicit val encodeExecutionStatus: EncodeJson[ExecutionStatus] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus](_.toString)

  implicit val decodeExecutionStatus: DecodeJson[ExecutionStatus] =
    DecodeJson[ExecutionStatus] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "ok" => DecodeResult.ok(ExecutionStatus.ok)
        case "error" => DecodeResult.ok(ExecutionStatus.error)
        case "abort" => DecodeResult.ok(ExecutionStatus.abort)
        case _ => DecodeResult.fail("Expected execution status (ok|error|abort)", c.history)
      }
    }

  implicit val encodeHistAccessType: EncodeJson[HistAccessType] =
    EncodeJson.StringEncodeJson.contramap[HistAccessType](_.toString)

  implicit val decodeHistAccessType: DecodeJson[HistAccessType] =
    DecodeJson[HistAccessType] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "range" => DecodeResult.ok(HistAccessType.range)
        case "tail" => DecodeResult.ok(HistAccessType.tail)
        case "search" => DecodeResult.ok(HistAccessType.search)
        case _ => DecodeResult.fail("Expected hist access type (range|tail|search)", c.history)
      }
    }

  implicit val encodeExecutionState: EncodeJson[ExecutionState] =
    EncodeJson.StringEncodeJson.contramap[ExecutionState](_.toString)

  implicit val decodeExecutionState: DecodeJson[ExecutionState] =
    DecodeJson[ExecutionState] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "busy" => DecodeResult.ok(ExecutionState.busy)
        case "idle" => DecodeResult.ok(ExecutionState.idle)
        case "starting" => DecodeResult.ok(ExecutionState.starting)
        case _ => DecodeResult.fail("Expected execution state (busy|idle|starting)", c.history)
      }
    }

  implicit val encodeClearOutput: EncodeJson[Output.ClearOutput] =
    EncodeJson[Output.ClearOutput] { c =>
      Json("wait" -> Json.jBool(c._wait))
    }

  implicit val decodeClearOutput: DecodeJson[Output.ClearOutput] =
    DecodeJson[Output.ClearOutput] { c =>
      c.--\("").focus match {
        case Some(b) => DecodeJson.BooleanDecodeJson.decodeJson(b).map(Output.ClearOutput)
        case None => DecodeResult.fail("ClearOutput", c.history)
      }
    }

  implicit def decodeEither[L: DecodeJson, R: DecodeJson]: DecodeJson[Either[L, R]] =
    DecodeJson[Either[L, R]] { c =>
      implicitly[DecodeJson[L]].decode(c).result match {
        case -\/(err) => implicitly[DecodeJson[R]].decode(c).map(Right(_))
        case \/-(l) => DecodeResult.ok(Left(l))
      }
    }

  implicit def encodeEither[L: EncodeJson, R: EncodeJson]: EncodeJson[Either[L, R]] =
    EncodeJson[Either[L, R]] {
      case Left(l) => implicitly[EncodeJson[L]].encode(l)
      case Right(r) => implicitly[EncodeJson[R]].encode(r)
    }
}


object Protocol {
  val version = (5, 0)
  val versionStrOpt: Option[String] = Some(s"${version._1}.${version._2}")
}

sealed trait ExecutionStatus
object ExecutionStatus {
  case object ok extends ExecutionStatus
  case object error extends ExecutionStatus
  case object abort extends ExecutionStatus
}

sealed trait HistAccessType
object HistAccessType {
  case object range extends HistAccessType
  case object tail extends HistAccessType
  case object search extends HistAccessType
}

sealed trait ExecutionState
object ExecutionState {
  case object busy extends ExecutionState
  case object idle extends ExecutionState
  case object starting extends ExecutionState
}

case class ArgSpec(
  args: List[String],
  varargs: String,
  varkw: String,
  defaults: List[String]
)


case class HeaderV4(
  msg_id: NbUUID,
  username: String,
  session: NbUUID,
  msg_type: String
) {
  def toHeader: Header =
    Header(
      msg_id = msg_id,
      username = username,
      session = session,
      msg_type = msg_type,
      version = None
    )
}

case class Header(
  msg_id: NbUUID,
  username: String,
  session: NbUUID,
  msg_type: String,
  version: Option[String]
)


case class ParsedMessage[Content](
  idents: List[String],
  header: Header,
  parent_header: Option[Header],
  metadata: Map[String, String],
  content: Content
) {
  import Formats._

  private def replyHeader(msgType: String): Header =
    header.copy(msg_id = NbUUID.randomUUID(), msg_type = msgType)

  private def replyMsg[ReplyContent: EncodeJson](
    idents: List[String], 
    msgType: String,
    content: ReplyContent, 
    metadata: Map[String, String]
  ): Message = {
    val m = ParsedMessage(idents, replyHeader(msgType), Some(header), metadata, content).toMessage

    if (header.version.isEmpty)
      m.protocolDown
    else
      m
  }

  def pub[PubContent: EncodeJson](
    msgType: String, 
    content: PubContent, 
    metadata: Map[String, String] = Map.empty
  ): Message = {
    val tpe = content match {
      case content: Output.Stream => content.name // ???
      case content: Output.StreamV4 => content.name // ???
      case _ => msgType.toString
    }
    
    replyMsg(tpe :: Nil, msgType, content, metadata)
  }

  def reply[ReplyContent: EncodeJson](
    msgType: String,
    content: ReplyContent, 
    metadata: Map[String, String] = Map.empty
  ): Message =
    replyMsg(idents, msgType, content, metadata)

  def toMessage(implicit encode: EncodeJson[Content]): Message =
    Message(
      idents,
      header.asJson.nospaces,
      parent_header.fold("{}")(_.asJson.nospaces),
      metadata.asJson.nospaces,
      content.asJson.nospaces
    )
}

object ParsedMessage {
  import Formats._

  def decode(message: Message): String \/ ParsedMessage[_] = {
    for {
      _header <- message.header.decodeEither[Header] orElse message.header.decodeEither[HeaderV4].map(_.toHeader)
      _parentHeader <- message.parentHeader.decodeEither[Option[Header]] orElse message.header.decodeEither[Option[HeaderV4]].map(_.map(_.toHeader))
      _metaData <- message.metaData.decodeEither[Map[String, String]]
      _content <- _header.msg_type match {
        case "execute_request"     => message.content.decodeEither[Input.ExecuteRequest]
        case "complete_request"    => message.content.decodeEither[Input.CompleteRequest]
        case "kernel_info_request" => message.content.decodeEither[Input.KernelInfoRequest]
        case "object_info_request" => message.content.decodeEither[Input.ObjectInfoRequest]
        case "connect_request"     => message.content.decodeEither[Input.ConnectRequest]
        case "shutdown_request"    => message.content.decodeEither[Input.ShutdownRequest]
        case "history_request"     => message.content.decodeEither[Input.HistoryRequest]
        case "input_reply"         => message.content.decodeEither[Input.InputReply]
        case "comm_open"           => message.content.decodeEither[InputOutput.CommOpen]
        case "comm_msg"            => message.content.decodeEither[InputOutput.CommMsg]
        case "comm_close"          => message.content.decodeEither[InputOutput.CommClose]
        case "execute_reply"       => message.content.decodeEither[Output.ExecuteOkReply]
          .orElse(message.content.decodeEither[Output.ExecuteErrorReply])
          .orElse(message.content.decodeEither[Output.ExecuteAbortReply])
        case "object_info_reply"   => message.content.decodeEither[Output.ObjectInfoNotFoundReply] orElse message.content.decodeEither[Output.ObjectInfoFoundReply]
        case "complete_reply"      => message.content.decodeEither[Output.CompleteReply]
        case "history_reply"       => message.content.decodeEither[Output.HistoryReply]
        case "connect_reply"       => message.content.decodeEither[Output.ConnectReply]
        case "kernel_info_reply"   => message.content.decodeEither[Output.KernelInfoReply] orElse message.content.decodeEither[Output.KernelInfoReplyV4].map(_.toKernelInfoReply)
        case "shutdown_reply"      => message.content.decodeEither[Output.ShutdownReply]
        case "stream"              => message.content.decodeEither[Output.Stream] orElse message.content.decodeEither[Output.StreamV4].map(_.toStream)
        case "display_data"        => message.content.decodeEither[Output.DisplayData]
        case "execute_input"       => message.content.decodeEither[Output.ExecuteInput]
        case "pyout"      => message.content.decodeEither[Output.PyOutV3].map(_.toExecuteResult) orElse message.content.decodeEither[Output.PyOutV4].map(_.toExecuteResult)
        case "execute_result"      => message.content.decodeEither[Output.ExecuteResult]
        case "pyerr"               => message.content.decodeEither[Output.PyErr].map(_.toError)
        case "error"               => message.content.decodeEither[Output.Error]
        case "status"              => message.content.decodeEither[Output.Status]
        case "meta_kernel_start_request"   => message.content.decodeEither[Meta.MetaKernelStartRequest]
        case "meta_kernel_start_reply"   => message.content.decodeEither[Meta.MetaKernelStartReply]
        case other                 => -\/(s"Unexpected message type: $other")
      }
    } yield ParsedMessage(message.idents, _header, _parentHeader, _metaData, _content)
  }
}

object InputOutput {

  case class CommOpen(
    comm_id: NbUUID,
    target_name: String,
    data: Json
  )

  case class CommMsg(
    comm_id: NbUUID,
    data: Json
  )

  case class CommClose(
    comm_id: NbUUID,
    data: Json
  )

}

object Input {

  case class ExecuteRequest(
    code: String,
    silent: Boolean,
    store_history: Option[Boolean] = None,
    user_expressions: Map[String, String],
    allow_stdin: Boolean
  )

  case class ObjectInfoRequest(
    oname: String,
    detail_level: Int
  )

  case class CompleteRequest(
    code: String,
    cursor_pos: Int
  )

  case class HistoryRequest(
    output: Boolean,
    raw: Boolean,
    hist_access_type: HistAccessType,
    session: Option[Int],
    start: Option[Int],
    stop: Option[Int],
    n: Option[Int],
    pattern: Option[String],
    unique: Option[Boolean]
  )

  case class ConnectRequest()

  case class KernelInfoRequest()

  case class ShutdownRequest(
    restart: Boolean
  )

  case class InputReply(
    value: String
  )

}

object Output {

  sealed trait ExecuteReply {
    val status: ExecutionStatus
    val execution_count: Int
  }

  case class ExecuteOkReply(
    execution_count: Int,
    payload: List[Map[String, String]] = Nil,
    user_expressions: Map[String, String] = Map.empty,
    status: ExecutionStatus.ok.type = ExecutionStatus.ok
  ) extends ExecuteReply

  case class ExecuteErrorReply(
    execution_count: Int,
    ename: String,
    evalue: String,
    traceback: List[String],
    status: ExecutionStatus.error.type = ExecutionStatus.error
  ) extends ExecuteReply

  case class ExecuteAbortReply(
    execution_count: Int,
    status: ExecutionStatus.abort.type = ExecutionStatus.abort
  ) extends ExecuteReply

  sealed trait ObjectInfoReply {
    val name: String
    val found: Boolean
  }

  case class ObjectInfoNotFoundReply(
    name: String,
    found: Boolean = false // FIXME Should be enforced by a singleton literal type
  ) extends ObjectInfoReply

  case class ObjectInfoFoundReply(
    name: String,
    ismagic: Option[Boolean] = None,
    isalias: Option[Boolean] = None,
    namespace: Option[String] = None,
    type_name: Option[String] = None,
    string_form: Option[String] = None,
    base_class: Option[String] = None,
    length: Option[String] = None,
    file: Option[String] = None,
    definition: Option[String] = None,
    argspec: Option[ArgSpec] = None,
    init_definition: Option[String] = None,
    docstring: Option[String] = None,
    init_docstring: Option[String] = None,
    class_docstring: Option[String] = None,
    call_def: Option[String] = None,
    call_docstring: Option[String] = None,
    source: Option[String] = None,
    found: Boolean = true // FIXME Should be enforced by a singleton literal type
  ) extends ObjectInfoReply

  case class CompleteReply(
    matches: List[String],
    cursor_start: Int,
    cursor_end: Int,
    status: ExecutionStatus
  )

  case class HistoryReply(
    history: List[(Int, Int, Either[String, (String, Option[String])])]
  )

  case class ConnectReply(
    shell_port: Int,
    iopub_port: Int,
    stdin_port: Int,
    hb_port: Int
  )

  case class LanguageInfo(
    name: String,
    codemirror_mode: String,
    file_extension: String,
    mimetype: String
    // pygments_lexer: String
  )

  case class KernelInfoReplyV4(
    protocol_version: List[Int],
    language: String
  ) {
    def toKernelInfoReply: KernelInfoReply =
      KernelInfoReply(
        protocol_version = protocol_version.map(_.toString) mkString ".",
        language_info = LanguageInfo(language, language, "", "")
      )
  }

  case class KernelInfoReply(
    protocol_version: String,
    language_info: LanguageInfo
  ) {
    def toKernelInfoReplyV4: KernelInfoReplyV4 =
      KernelInfoReplyV4(
        protocol_version = protocol_version.split('.').toList.flatMap(s => Try(s.toInt).toOption.toList),
        language = language_info.name
      )
  }

  case class ShutdownReply(
    restart: Boolean
  )

  case class StreamV4(
    name: String,
    data: String
  ) {
    def toStream: Stream =
      Stream(
        name = name,
        text = data
      )
  }

  case class Stream(
    name: String,
    text: String
  ) {
    def toStreamV4: StreamV4 =
      StreamV4(
        name = name,
        data = text
      )
  }

  case class DisplayData(
    source: String,
    data: Map[String, String],
    metadata: Map[String, String]
  )

  case class ExecuteInput(
    code: String,
    execution_count: Int
  )

  // Pre 4.0
  case class PyOutV3(
    prompt_number: Int,
    data: Map[String, String],
    metadata: Map[String, String] = Map.empty
  ) {
    def toExecuteResult: ExecuteResult =
      ExecuteResult(prompt_number, data, metadata)
  }

  case class PyOutV4(
    execution_count: Int,
    data: Map[String, String],
    metadata: Map[String, String] = Map.empty
  ) {
    def toExecuteResult: ExecuteResult =
      ExecuteResult(execution_count, data, metadata)
  }

  case class ExecuteResult(
    execution_count: Int,
    data: Map[String, String],
    metadata: Map[String, String] = Map.empty
  ) {
    def toPyOut1: PyOutV4 =
      PyOutV4(
        execution_count = execution_count,
        data = data,
        metadata = metadata
      )
  }

  // Pre 4.0
  case class PyErr(
    ename: String,
    evalue: String,
    traceback: List[String]
  ) {
    def toError: Error =
      Error(-1, ename, evalue, traceback)
  }

  case class Error(
    execution_count: Int, // Should be removed?
    ename: String,
    evalue: String,
    traceback: List[String]
  )

  object Error {
    def apply(execution_count: Int, exception: Throwable): Error = {
      val name = exception.getClass.getName
      val value = Option(exception.getMessage) getOrElse ""
      val stackTrace = exception.getStackTrace.takeWhile(_.getFileName != "<console>").toList
      val traceBack = s"$name: $value" :: stackTrace.map("    " + _)

      Error(
        execution_count=execution_count,
        ename=name,
        evalue=value,
        traceback=traceBack
      )
    }
  }

  case class Status(
    execution_state: ExecutionState
  )

  case class ClearOutput(
    _wait: Boolean
  )

  case class InputRequest(
    prompt: String
  )

}
