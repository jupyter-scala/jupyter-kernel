package jupyter
package kernel

import argonaut._, Argonaut.{ EitherDecodeJson => _, _ }
import protocol._, Formats._

import scalaz.\/

import scala.language.implicitConversions


case class KernelInfo(
  name: String,
  language: String
)

trait Kernel


sealed trait Channel extends Product with Serializable

object Channel {
  case object   Publish extends Channel
  case object  Requests extends Channel
  case object   Control extends Channel
  case object     Input extends Channel

  val channels = Seq(Requests, Publish, Control, Input)
}

object Message {
  implicit def parsedMessageToMessage[T: EncodeJson](p: ParsedMessage[T]): Message = p.toMessage
}

case class Message(
  idents: List[Seq[Byte]],
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
     |  "header": $header,
     |  "parent_header": $parentHeader,
     |  "metadata": $metaData,
     |  "content": $content
     |}
   """.stripMargin

  def decode: String \/ ParsedMessage[_] =
    ParsedMessage.decode(this)

}
