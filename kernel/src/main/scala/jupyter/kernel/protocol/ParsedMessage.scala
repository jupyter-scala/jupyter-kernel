package jupyter
package kernel
package protocol

import argonaut.{EncodeJson, Json}
import _root_.scala.util.Try

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
  private def replyHeader(msgType: String): Header =
    header.copy(msg_id = NbUUID.randomUUID(), msg_type = msgType)

  private def replyMsg[ReplyContent: EncodeJson](
    idents: List[String], 
    msgType: String,
    content: ReplyContent, 
    metadata: Map[String, String]
  ): Message = {
    val m = Message(ParsedMessage(idents, replyHeader(msgType), Some(header), metadata, content))

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
    payload: List[Map[String, String]],
    user_expressions: Map[String, String],
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

case class Notebook(
  nbformat: Int,
  nbformat_minor: Int,
  metadata: Map[String, String],
  cells: List[String]
)
