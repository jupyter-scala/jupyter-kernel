package jupyter
package kernel

import argonaut._, Argonaut.{ EitherDecodeJson => _, _ }
import protocol._, Formats._

import scalaz.{-\/, \/}


case class KernelInfo(
  name: String,
  language: String
)

trait Kernel


sealed trait Channel

object Channel {
  case object   Publish extends Channel
  case object  Requests extends Channel
  case object   Control extends Channel
  case object     Input extends Channel

  val channels = Seq(Requests, Publish, Control, Input)
}


case class Message(
  idents: List[String],
  header: String,
  parentHeader: String,
  metaData: String,
  content: String
) {
  def protocolUp: Message = {
    def pyoutToExecuteResult =
      for {
        header <- header.parseOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "pyout"
        _content <- content.parseOption
      } yield copy(
        header = (("msg_type" -> Json.jString("execute_result")) ->: header).spaces2,
        content = _content.field("prompt_number") match {
          case None =>
            content
          case Some(n) =>
            (("execution_count" -> n) ->: _content.hcursor.--\("prompt_number").delete.focus.get).spaces2
        }
      )

    def pyerrToError =
      for {
        header <- header.parseOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "pyerr"
      } yield copy(header = (("msg_type" -> Json.jString("error")) ->: header).spaces2)

    def kernelInfoReplyNewer =
      for {
        header <- header.parseOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "kernel_info_reply"
        reply0 <- content.decodeOption[Output.KernelInfoReplyV4]
      } yield copy(content = reply0.toKernelInfoReply.asJson.nospaces)

    (pyoutToExecuteResult orElse pyerrToError orElse kernelInfoReplyNewer) getOrElse this
  }

  def protocolDown: Message = {
    lazy val headerOption = header.parseOption

    def executeResultToPyOut =
      for {
        header <- headerOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "execute_result"
        _content <- content.parseOption
      } yield copy(
        header = (("msg_type" -> Json.jString("pyout")) ->: header).nospaces
      )

    def errorToPyerr =
      for {
        header <- headerOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "error"
      } yield copy(header = (("msg_type" -> Json.jString("pyerr")) ->: header).nospaces)

    def kernelInfoReplyOlder =
      for {
        header <- headerOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "kernel_info_reply"
        reply0 <- content.decodeOption[Output.KernelInfoReply]
      } yield copy(content = reply0.toKernelInfoReplyV4.asJson.nospaces)

    def streamOlder =
      for {
        header <- headerOption
        msgType <- header.field("msg_type").flatMap(_.as[String].toOption) if msgType == "stream"
        reply0 <- content.decodeOption[Output.Stream]
      } yield copy(content = reply0.toStreamV4.asJson.nospaces)

    (executeResultToPyOut orElse errorToPyerr orElse kernelInfoReplyOlder orElse streamOlder) getOrElse this
  }

  def toJsonStr: String =
    s"""{
     |  "idents": ${idents.map(_.asJson.nospaces).mkString("[", ", ", "]")},
     |  "header": $header,
     |  "parent_header": $parentHeader,
     |  "metadata": $metaData,
     |  "content": $content
     |}
   """.stripMargin

  def decode: String \/ ParsedMessage[_] = {
    for {
      _header <- header.decodeEither[Header] orElse header.decodeEither[HeaderV4].map(_.toHeader)
      _parentHeader <- parentHeader.decodeEither[Option[Header]] orElse header.decodeEither[Option[HeaderV4]].map(_.map(_.toHeader))
      _metaData <- metaData.decodeEither[Map[String, String]]
      _content <- _header.msg_type match {
        case "execute_request"     => content.decodeEither[Input.ExecuteRequest]
        case "complete_request"    => content.decodeEither[Input.CompleteRequest]
        case "kernel_info_request" => content.decodeEither[Input.KernelInfoRequest]
        case "object_info_request" => content.decodeEither[Input.ObjectInfoRequest]
        case "connect_request"     => content.decodeEither[Input.ConnectRequest]
        case "shutdown_request"    => content.decodeEither[Input.ShutdownRequest]
        case "history_request"     => content.decodeEither[Input.HistoryRequest]
        case "input_reply"         => content.decodeEither[Input.InputReply]
        case "comm_open"           => content.decodeEither[InputOutput.CommOpen]
        case "comm_msg"            => content.decodeEither[InputOutput.CommMsg]
        case "comm_close"          => content.decodeEither[InputOutput.CommClose]
        case "execute_reply"       => content.decodeEither[Output.ExecuteOkReply]
                                       .orElse(content.decodeEither[Output.ExecuteErrorReply])
                                       .orElse(content.decodeEither[Output.ExecuteAbortReply])
        case "object_info_reply"   => content.decodeEither[Output.ObjectInfoNotFoundReply] orElse content.decodeEither[Output.ObjectInfoFoundReply]
        case "complete_reply"      => content.decodeEither[Output.CompleteReply]
        case "history_reply"       => content.decodeEither[Output.HistoryReply]
        case "connect_reply"       => content.decodeEither[Output.ConnectReply]
        case "kernel_info_reply"   => content.decodeEither[Output.KernelInfoReply] orElse content.decodeEither[Output.KernelInfoReplyV4].map(_.toKernelInfoReply)
        case "shutdown_reply"      => content.decodeEither[Output.ShutdownReply]
        case "stream"              => content.decodeEither[Output.Stream] orElse content.decodeEither[Output.StreamV4].map(_.toStream)
        case "display_data"        => content.decodeEither[Output.DisplayData]
        case "execute_input"       => content.decodeEither[Output.ExecuteInput]
        case "pyout"      => content.decodeEither[Output.PyOutV3].map(_.toExecuteResult) orElse content.decodeEither[Output.PyOutV4].map(_.toExecuteResult)
        case "execute_result"      => content.decodeEither[Output.ExecuteResult]
        case "pyerr"               => content.decodeEither[Output.PyErr].map(_.toError)
        case "error"               => content.decodeEither[Output.Error]
        case "status"              => content.decodeEither[Output.Status]
        case "meta_kernel_start_request"   => content.decodeEither[Meta.MetaKernelStartRequest]
        case "meta_kernel_start_reply"   => content.decodeEither[Meta.MetaKernelStartReply]
        case other                 => -\/(s"Unexpected message type: $other")
      }
    } yield ParsedMessage(idents, _header, _parentHeader, _metaData, _content)
  }
}
