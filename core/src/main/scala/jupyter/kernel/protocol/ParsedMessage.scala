package jupyter
package kernel
package protocol

import java.util.UUID

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }

import shapeless.Witness

import scala.util.Try

import scalaz.{ -\/, \/, \/-, Memo }

object Printer {

  import PrettyParams.nospace._

  import Memo._

  private def vectorMemo() = {
    var vector: Vector[String] = Vector.empty

    val memoFunction: (Int => String) => Int => String = f => k => {
      val localVector = vector
      val adjustedK = if (k < 0) 0 else k
      if (localVector.size > adjustedK) {
        localVector(adjustedK)
      } else {
        val newVector = Vector.tabulate(adjustedK + 1)(f)
        vector = newVector
        newVector.last
      }
    }
    memo(memoFunction)
  }

  private def addIndentation(s: String): Int => String = {
    val lastNewLineIndex = s.lastIndexOf("\n")
    if (lastNewLineIndex < 0) {
      _ => s
    } else {
      val afterLastNewLineIndex = lastNewLineIndex + 1
      val start = s.substring(0, afterLastNewLineIndex)
      val end = s.substring(afterLastNewLineIndex)
      n => start + indent * n + end
    }
  }

  private val openBraceText = "{"
  private val closeBraceText = "}"
  private val openArrayText = "["
  private val closeArrayText = "]"
  private val commaText = ","
  private val colonText = ":"
  private val nullText = "null"
  private val trueText = "true"
  private val falseText = "false"
  private val stringEnclosureText = "\""

  private val _lbraceLeft = addIndentation(lbraceLeft)
  private val _lbraceRight = addIndentation(lbraceRight)
  private val _rbraceLeft = addIndentation(rbraceLeft)
  private val _rbraceRight = addIndentation(rbraceRight)
  private val _lbracketLeft = addIndentation(lbracketLeft)
  private val _lbracketRight = addIndentation(lbracketRight)
  private val _rbracketLeft = addIndentation(rbracketLeft)
  private val _rbracketRight = addIndentation(rbracketRight)
  private val _lrbracketsEmpty = addIndentation(lrbracketsEmpty)
  private val _arrayCommaLeft = addIndentation(arrayCommaLeft)
  private val _arrayCommaRight = addIndentation(arrayCommaRight)
  private val _objectCommaLeft = addIndentation(objectCommaLeft)
  private val _objectCommaRight = addIndentation(objectCommaRight)
  private val _colonLeft = addIndentation(colonLeft)
  private val _colonRight = addIndentation(colonRight)

  private val lbraceMemo = vectorMemo(){depth: Int => "%s%s%s".format(_lbraceLeft(depth), openBraceText, _lbraceRight(depth + 1))}
  private val rbraceMemo = vectorMemo(){depth: Int => "%s%s%s".format(_rbraceLeft(depth), closeBraceText, _rbraceRight(depth + 1))}

  private val lbracketMemo = vectorMemo(){depth: Int => "%s%s%s".format(_lbracketLeft(depth), openArrayText, _lbracketRight(depth + 1))}
  private val rbracketMemo = vectorMemo(){depth: Int => "%s%s%s".format(_rbracketLeft(depth), closeArrayText, _rbracketRight(depth))}
  private val lrbracketsEmptyMemo = vectorMemo(){depth: Int => "%s%s%s".format(openArrayText, _lrbracketsEmpty(depth), closeArrayText)}
  private val arrayCommaMemo = vectorMemo(){depth: Int => "%s%s%s".format(_arrayCommaLeft(depth + 1), commaText, _arrayCommaRight(depth + 1))}
  private val objectCommaMemo = vectorMemo(){depth: Int => "%s%s%s".format(_objectCommaLeft(depth + 1), commaText, _objectCommaRight(depth + 1))}
  private val colonMemo = vectorMemo(){depth: Int => "%s%s%s".format(_colonLeft(depth + 1), colonText, _colonRight(depth + 1))}

  /**
    * Returns a string representation of a pretty-printed JSON value.
    */
  final def noSpaces(j: Json): String = {

    import Json._
    import StringEscaping._

    def appendJsonString(builder: StringBuilder, jsonString: String): StringBuilder = {
      for (c <- jsonString) {
        if (isNormalChar(c))
          builder += c
        else
          builder.append(escape(c))
      }

      builder
    }

    def encloseJsonString(builder: StringBuilder, jsonString: JsonString): StringBuilder = {
      appendJsonString(builder.append(stringEnclosureText), jsonString).append(stringEnclosureText)
    }

    def trav(builder: StringBuilder, depth: Int, k: Json): StringBuilder = {

      def lbrace(builder: StringBuilder): StringBuilder = {
        builder.append(lbraceMemo(depth))
      }
      def rbrace(builder: StringBuilder): StringBuilder = {
        builder.append(rbraceMemo(depth))
      }
      def lbracket(builder: StringBuilder): StringBuilder = {
        builder.append(lbracketMemo(depth))
      }
      def rbracket(builder: StringBuilder): StringBuilder = {
        builder.append(rbracketMemo(depth))
      }
      def lrbracketsEmpty(builder: StringBuilder): StringBuilder = {
        builder.append(lrbracketsEmptyMemo(depth))
      }
      def arrayComma(builder: StringBuilder): StringBuilder = {
        builder.append(arrayCommaMemo(depth))
      }
      def objectComma(builder: StringBuilder): StringBuilder = {
        builder.append(objectCommaMemo(depth))
      }
      def colon(builder: StringBuilder): StringBuilder = {
        builder.append(colonMemo(depth))
      }

      k.fold[StringBuilder](
        builder.append(nullText)
        , bool => builder.append(if (bool) trueText else falseText)
        , n => n match {
          case JsonLong(x) => builder append x.toString
          case JsonDouble(x) => builder append x.toString
          case JsonDecimal(x) => builder append x
          case JsonBigDecimal(x) => builder append x.toString
        }
        , s => encloseJsonString(builder, s)
        , e => if (e.isEmpty) {
          lrbracketsEmpty(builder)
        } else {
          rbracket(e.foldLeft((true, lbracket(builder))){case ((firstElement, builder), subElement) =>
            val withComma = if (firstElement) builder else arrayComma(builder)
            val updatedBuilder = trav(withComma, depth + 1, subElement)
            (false, updatedBuilder)
          }._2)
        }
        , o => {
          rbrace((if (preserveOrder) o.toList else o.toMap).foldLeft((true, lbrace(builder))){case ((firstElement, builder), (key, value)) =>
            val ignoreKey = dropNullKeys && value.isNull
            if (ignoreKey) {
              (firstElement, builder)
            } else {
              val withComma = if (firstElement) builder else objectComma(builder)
              (false, trav(colon(encloseJsonString(withComma, key)), depth + 1, value))
            }
          }._2)
        }
      )
    }

    trav(new StringBuilder(), 0, j).toString()
  }

}

object Formats {
  import Shapeless._

  implicit lazy val commOpenDecodeJson = DecodeJson.of[InputOutput.CommOpen]
  implicit lazy val commMsgDecodeJson = DecodeJson.of[InputOutput.CommMsg]
  implicit lazy val commCloseDecodeJson = DecodeJson.of[InputOutput.CommClose]
  implicit lazy val commOpenEncodeJson = EncodeJson.of[InputOutput.CommOpen]
  implicit lazy val commMsgEncodeJson = EncodeJson.of[InputOutput.CommMsg]
  implicit lazy val commCloseEncodeJson = EncodeJson.of[InputOutput.CommClose]

  implicit lazy val executeResultDecodeJson = DecodeJson.of[Output.ExecuteResult]
  implicit lazy val metaKernelStartReplyDecodeJson = DecodeJson.of[Meta.MetaKernelStartReply]


  implicit lazy val inputExecuteRequestDecodeJson = DecodeJson.of[Input.ExecuteRequest]
  implicit lazy val inputCompleteRequestDecodeJson = DecodeJson.of[Input.CompleteRequest]
  implicit lazy val inputKernelInfoRequestDecodeJson = DecodeJson.of[Input.KernelInfoRequest]
  implicit lazy val inputObjectInfoRequestDecodeJson = DecodeJson.of[Input.ObjectInfoRequest]
  implicit lazy val inputConnectRequestDecodeJson = DecodeJson.of[Input.ConnectRequest]
  implicit lazy val inputShutdownRequestDecodeJson = DecodeJson.of[Input.ShutdownRequest]
  implicit lazy val inputHistoryRequestDecodeJson = DecodeJson.of[Input.HistoryRequest]
  implicit lazy val inputInputReplyDecodeJson = DecodeJson.of[Input.InputReply]
  implicit lazy val outputExecuteOkReplyDecodeJson = DecodeJson.of[Output.ExecuteOkReply]

  implicit lazy val outputExecuteErrorReplyDecodeJson = DecodeJson.of[Output.ExecuteErrorReply]
  implicit lazy val outputExecuteAbortReplyDecodeJson = DecodeJson.of[Output.ExecuteAbortReply]
  implicit lazy val outputObjectInfoNotFoundReplyDecodeJson = DecodeJson.of[Output.ObjectInfoNotFoundReply]
  implicit lazy val outputObjectInfoFoundReplyDecodeJson = DecodeJson.of[Output.ObjectInfoFoundReply]
  implicit lazy val outputCompleteReplyDecodeJson = DecodeJson.of[Output.CompleteReply]
  implicit lazy val outputHistoryReplyDecodeJson = DecodeJson.of[Output.HistoryReply]
  implicit lazy val outputConnectReplyDecodeJson = DecodeJson.of[Output.ConnectReply]
  implicit lazy val outputKernelInfoReplyDecodeJson = DecodeJson.of[Output.KernelInfoReply]
  implicit lazy val outputKernelInfoReplyV4DecodeJson = DecodeJson.of[Output.KernelInfoReplyV4]
  implicit lazy val outputShutdownReplyDecodeJson = DecodeJson.of[Output.ShutdownReply]
  implicit lazy val outputStreamDecodeJson = DecodeJson.of[Output.Stream]
  implicit lazy val outputStreamV4DecodeJson = DecodeJson.of[Output.StreamV4]
  implicit lazy val outputDisplayDataDecodeJson = DecodeJson.of[Output.DisplayData]
  implicit lazy val outputExecuteInputDecodeJson = DecodeJson.of[Output.ExecuteInput]
  implicit lazy val outputPyOutV3DecodeJson = DecodeJson.of[Output.PyOutV3]
  implicit lazy val outputPyOutV4DecodeJson = DecodeJson.of[Output.PyOutV4]
  implicit lazy val outputPyErrDecodeJson = DecodeJson.of[Output.PyErr]
  implicit lazy val outputErrorDecodeJson = DecodeJson.of[Output.Error]
  implicit lazy val outputStatusDecodeJson = DecodeJson.of[Output.Status]
  implicit lazy val metaMetaKernelStartRequestDecodeJson = DecodeJson.of[Meta.MetaKernelStartRequest]


  implicit lazy val inputExecuteRequestEncodeJson = EncodeJson.of[Input.ExecuteRequest]
  implicit lazy val inputCompleteRequestEncodeJson = EncodeJson.of[Input.CompleteRequest]
  implicit lazy val inputKernelInfoRequestEncodeJson = EncodeJson.of[Input.KernelInfoRequest]
  implicit lazy val inputObjectInfoRequestEncodeJson = EncodeJson.of[Input.ObjectInfoRequest]
  implicit lazy val inputConnectRequestEncodeJson = EncodeJson.of[Input.ConnectRequest]
  implicit lazy val inputShutdownRequestEncodeJson = EncodeJson.of[Input.ShutdownRequest]
  implicit lazy val inputHistoryRequestEncodeJson = EncodeJson.of[Input.HistoryRequest]
  implicit lazy val inputInputReplyEncodeJson = EncodeJson.of[Input.InputReply]
  implicit lazy val outputExecuteOkReplyEncodeJson = EncodeJson.of[Output.ExecuteOkReply]

  implicit lazy val outputExecuteErrorReplyEncodeJson = EncodeJson.of[Output.ExecuteErrorReply]
  implicit lazy val outputExecuteAbortReplyEncodeJson = EncodeJson.of[Output.ExecuteAbortReply]
  implicit lazy val outputObjectInfoNotFoundReplyEncodeJson = EncodeJson.of[Output.ObjectInfoNotFoundReply]
  implicit lazy val outputObjectInfoFoundReplyEncodeJson = EncodeJson.of[Output.ObjectInfoFoundReply]
  implicit lazy val outputCompleteReplyEncodeJson = EncodeJson.of[Output.CompleteReply]
  implicit lazy val outputHistoryReplyEncodeJson = EncodeJson.of[Output.HistoryReply]
  implicit lazy val outputConnectReplyEncodeJson = EncodeJson.of[Output.ConnectReply]
  implicit lazy val outputKernelInfoReplyEncodeJson = EncodeJson.of[Output.KernelInfoReply]
  implicit lazy val outputKernelInfoReplyV4EncodeJson = EncodeJson.of[Output.KernelInfoReplyV4]
  implicit lazy val outputShutdownReplyEncodeJson = EncodeJson.of[Output.ShutdownReply]
  implicit lazy val outputStreamEncodeJson = EncodeJson.of[Output.Stream]
  implicit lazy val outputStreamV4EncodeJson = EncodeJson.of[Output.StreamV4]
  implicit lazy val outputDisplayDataEncodeJson = EncodeJson.of[Output.DisplayData]
  implicit lazy val outputExecuteInputEncodeJson = EncodeJson.of[Output.ExecuteInput]
  implicit lazy val outputPyOutV3EncodeJson = EncodeJson.of[Output.PyOutV3]
  implicit lazy val outputPyOutV4EncodeJson = EncodeJson.of[Output.PyOutV4]
  implicit lazy val outputExecuteResultEncodeJson = EncodeJson.of[Output.ExecuteResult]
  implicit lazy val outputPyErrEncodeJson = EncodeJson.of[Output.PyErr]
  implicit lazy val outputErrorEncodeJson = EncodeJson.of[Output.Error]
  implicit lazy val outputStatusEncodeJson = EncodeJson.of[Output.Status]
  implicit lazy val metaMetaKernelStartRequestEncodeJson = EncodeJson.of[Meta.MetaKernelStartRequest]
  implicit lazy val metaMetaKernelStartReplyEncodeJson = EncodeJson.of[Meta.MetaKernelStartReply]

  implicit lazy val headerDecodeJson = DecodeJson.of[Header]
  implicit lazy val headerEncodeJson = EncodeJson.of[Header]
  implicit lazy val headerV4DecodeJson = DecodeJson.of[HeaderV4]
  implicit lazy val headerV4EncodeJson = EncodeJson.of[HeaderV4]

  implicit lazy val connectionDecodeJson = DecodeJson.of[Connection]
  implicit lazy val connectionEncodeJson = EncodeJson.of[Connection]

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
      c.--\("wait").focus match {
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
  val versionMajor = 5
  val versionMinor = 0

  val versionStrOpt: Option[String] = Some(s"$versionMajor.$versionMinor")
}

sealed trait ExecutionStatus extends Product with Serializable
object ExecutionStatus {
  case object ok extends ExecutionStatus
  case object error extends ExecutionStatus
  case object abort extends ExecutionStatus
}

sealed trait HistAccessType extends Product with Serializable
object HistAccessType {
  case object range extends HistAccessType
  case object tail extends HistAccessType
  case object search extends HistAccessType
}

sealed trait ExecutionState extends Product with Serializable
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
  msg_id: String,
  username: String,
  session: String,
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
  msg_id: String,
  username: String,
  session: String,
  msg_type: String,
  version: Option[String]
)


case class ParsedMessage[Content](
  idents: List[Seq[Byte]],
  header: Header,
  parent_header: Option[Header],
  metadata: Map[String, String],
  content: Content
) {
  import Formats._

  private def replyHeader(msgType: String): Header =
    header.copy(msg_id = UUID.randomUUID().toString, msg_type = msgType)

  private def replyMsg[ReplyContent: EncodeJson](
    idents: List[Seq[Byte]],
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
      case _ => msgType
    }

    replyMsg(tpe.getBytes("UTF-8") :: Nil, msgType, content, metadata)
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
      Printer.noSpaces(header.asJson),
      parent_header.fold("{}")(x => Printer.noSpaces(x.asJson)),
      Printer.noSpaces(metadata.asJson),
      Printer.noSpaces(content.asJson)
    )
}

object ParsedMessage {
  import Formats._

  def decodeHeader(str: String): String \/ Header =
    str.decodeEither[Header]
      .orElse(str.decodeEither[HeaderV4].map(_.toHeader))

  def decodeHeaderOption(str: String): String \/ Option[Header] =
    str.decodeEither[Option[Header]]
      .orElse(str.decodeEither[Option[HeaderV4]].map(_.map(_.toHeader)))

  def decodeMetaData(str: String): String \/ Map[String, String] =
    str.decodeEither[Map[String, String]]

  def decodeInputRequest(msgType: String, str: String): Option[String \/ Any] = // FIXME
    msgType match {
      case "execute_request"     => Some(str.decodeEither[Input.ExecuteRequest])
      case "complete_request"    => Some(str.decodeEither[Input.CompleteRequest])
      case "kernel_info_request" => Some(str.decodeEither[Input.KernelInfoRequest])
      case "object_info_request" => Some(str.decodeEither[Input.ObjectInfoRequest])
      case "connect_request"     => Some(str.decodeEither[Input.ConnectRequest])
      case "shutdown_request"    => Some(str.decodeEither[Input.ShutdownRequest])
      case "history_request"     => Some(str.decodeEither[Input.HistoryRequest])
      case "input_reply"         => Some(str.decodeEither[Input.InputReply])
      case _                     => None
    }

  def decodeInputOutputMessage(msgType: String, str: String): Option[String \/ Any] = // FIXME
    msgType match {
      case "comm_open"           => Some(str.decodeEither[InputOutput.CommOpen])
      case "comm_msg"            => Some(str.decodeEither[InputOutput.CommMsg])
      case "comm_close"          => Some(str.decodeEither[InputOutput.CommClose])
      case _                     => None
    }

  def decodeReply(msgType: String, str: String): Option[String \/ Any] = // FIXME
    msgType match {
      case "execute_reply"       => Some(
        str.decodeEither[Output.ExecuteOkReply]
          .orElse(str.decodeEither[Output.ExecuteErrorReply])
          .orElse(str.decodeEither[Output.ExecuteAbortReply])
      )
      case "object_info_reply"   => Some(
        str.decodeEither[Output.ObjectInfoNotFoundReply]
          .orElse(str.decodeEither[Output.ObjectInfoFoundReply])
      )
      case "complete_reply"      => Some(str.decodeEither[Output.CompleteReply])
      case "history_reply"       => Some(str.decodeEither[Output.HistoryReply])
      case "connect_reply"       => Some(str.decodeEither[Output.ConnectReply])
      case "kernel_info_reply"   => Some(
        str.decodeEither[Output.KernelInfoReply]
          .orElse(str.decodeEither[Output.KernelInfoReplyV4].map(_.toKernelInfoReply))
      )
      case "shutdown_reply"      => Some(str.decodeEither[Output.ShutdownReply])
      case _                     => None
    }
  
  def decodeElem(msgType: String, str: String): Option[String \/ Any] = // FIXME
    msgType match {
      case "stream"              => Some(
        str.decodeEither[Output.Stream]
          .orElse(str.decodeEither[Output.StreamV4].map(_.toStream))
      )
      case "display_data"        => Some(str.decodeEither[Output.DisplayData])
      case "execute_input"       => Some(str.decodeEither[Output.ExecuteInput])
      case "pyout"               => Some(
        str.decodeEither[Output.PyOutV3].map(_.toExecuteResult)
          .orElse(str.decodeEither[Output.PyOutV4].map(_.toExecuteResult))
      )
      case "execute_result"      => Some(str.decodeEither[Output.ExecuteResult])
      case "pyerr"               => Some(str.decodeEither[Output.PyErr].map(_.toError))
      case "error"               => Some(str.decodeEither[Output.Error])
      case "status"              => Some(str.decodeEither[Output.Status])
      case _                     => None
    }

  def decodeMetaKernelMessage(msgType: String, str: String): Option[String \/ Any] = // FIXME
    msgType match {
      case "meta_kernel_start_request" => Some(str.decodeEither[Meta.MetaKernelStartRequest])
      case "meta_kernel_start_reply"   => Some(str.decodeEither[Meta.MetaKernelStartReply])
      case _                           => None
    }

  def decode(message: Message): String \/ ParsedMessage[_] =
    for {
      header0 <- decodeHeader(message.header)
      parentHeader0 <- decodeHeaderOption(message.parentHeader)
      metaData0 <- decodeMetaData(message.metaData)
      content0 <-
        decodeInputRequest(header0.msg_type, message.content)
          .orElse(decodeInputOutputMessage(header0.msg_type, message.content))
          .orElse(decodeReply(header0.msg_type, message.content))
          .orElse(decodeElem(header0.msg_type, message.content))
          .orElse(decodeMetaKernelMessage(header0.msg_type, message.content))
          .getOrElse(\/-(s"Unexpected message type: ${header0.msg_type}"))
    } yield ParsedMessage(message.idents, header0, parentHeader0, metaData0, content0)
}

object InputOutput {

  trait Comm

  case class CommOpen(
    comm_id: String,
    target_name: String,
    data: Json
  ) extends Comm

  case class CommMsg(
    comm_id: String,
    data: Json
  ) extends Comm

  case class CommClose(
    comm_id: String,
    data: Json
  ) extends Comm

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

  val True = Witness(true).value
  type True = Witness.`true`.T

  val False = Witness(false).value
  type False = Witness.`false`.T


  sealed trait ExecuteReply extends Product with Serializable {
    val status: ExecutionStatus
    val execution_count: Int
  }

  // FIXME These aliases seem to be required in 2.10 for codec derivation to be fine.
  // Don't know why. Probably an issue at the argonaut-shapeless or shapeless itself level.
  type ExecutionStatusOkType = ExecutionStatus.ok.type
  type ExecutionStatusErrorType = ExecutionStatus.error.type
  type ExecutionStatusAbortType = ExecutionStatus.abort.type

  case class ExecuteOkReply(
    execution_count: Int,
    payload: List[Map[String, String]],
    user_expressions: Map[String, String],
    status: ExecutionStatusOkType
  ) extends ExecuteReply

  object ExecuteOkReply {
    def apply(
      execution_count: Int,
      payload: List[Map[String, String]] = Nil,
      user_expressions: Map[String, String] = Map.empty
    ): ExecuteOkReply =
      ExecuteOkReply(
        execution_count, payload, user_expressions, ExecutionStatus.ok
      )
  }

  case class ExecuteErrorReply(
    execution_count: Int,
    ename: String,
    evalue: String,
    traceback: List[String],
    status: ExecutionStatusErrorType
  ) extends ExecuteReply

  object ExecuteErrorReply {
    def apply(
      execution_count: Int,
      ename: String,
      evalue: String,
      traceback: List[String]
    ): ExecuteErrorReply =
      ExecuteErrorReply(execution_count, ename, evalue, traceback, ExecutionStatus.error)
  }

  case class ExecuteAbortReply(
    execution_count: Int,
    status: ExecutionStatusAbortType
  ) extends ExecuteReply

  object ExecuteAbortReply {
    def apply(
      execution_count: Int
    ): ExecuteAbortReply =
      ExecuteAbortReply(execution_count, ExecutionStatus.abort)
  }

  sealed trait ObjectInfoReply extends Product with Serializable {
    val name: String
    val found: Boolean
  }

  case class ObjectInfoNotFoundReply(
    name: String,
    found: False
  ) extends ObjectInfoReply

  object ObjectInfoNotFoundReply {
    def apply(name: String): ObjectInfoNotFoundReply =
      ObjectInfoNotFoundReply(name, False)
  }

  case class ObjectInfoFoundReply(
    name: String,
    ismagic: Option[Boolean],
    isalias: Option[Boolean],
    namespace: Option[String],
    type_name: Option[String],
    string_form: Option[String],
    base_class: Option[String],
    length: Option[String],
    file: Option[String],
    definition: Option[String],
    argspec: Option[ArgSpec],
    init_definition: Option[String],
    docstring: Option[String],
    init_docstring: Option[String],
    class_docstring: Option[String],
    call_def: Option[String],
    call_docstring: Option[String],
    source: Option[String],
    found: True
  ) extends ObjectInfoReply

  object ObjectInfoFoundReply {
    def apply(
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
      source: Option[String] = None
    ): ObjectInfoFoundReply =
      ObjectInfoFoundReply(
        name,
        ismagic,
        isalias,
        namespace,
        type_name,
        string_form,
        base_class,
        length,
        file,
        definition,
        argspec,
        init_definition,
        docstring,
        init_docstring,
        class_docstring,
        call_def,
        call_docstring,
        source,
        True
      )
  }

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
    version: String,
    codemirror_mode: String,
    file_extension: String,
    mimetype: String,
    pygments_lexer: String
  )

  object LanguageInfo {
    val empty = LanguageInfo("", "", "", "", "", "")
  }

  case class KernelInfoReplyV4(
    protocol_version: List[Int],
    language: String
  ) {
    def toKernelInfoReply: KernelInfoReply =
      KernelInfoReply(
        protocol_version = protocol_version.map(_.toString) mkString ".",
        language_info = LanguageInfo(language, "", language, "", "", language),
        implementation = "",
        implementation_version = "",
        banner = ""
      )
  }

  case class KernelInfoReply(
    protocol_version: String,
    implementation: String,
    implementation_version: String,
    language_info: LanguageInfo,
    banner: String
    // help_links: Map[String, String] // optional
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
