package jupyter.kernel.protocol

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }

import java.util.UUID

import jupyter.kernel.Message
import jupyter.kernel.protocol.Formats.{ decodeHeader, encodeHeader }

case class ParsedMessage[Content](
  idents: List[Seq[Byte]],
  header: Header,
  parent_header: Option[Header],
  metadata: Map[String, String],
  content: Content
) {

  private def replyHeader(msgType: String): Header =
    header.copy(msg_id = UUID.randomUUID().toString, msg_type = msgType)

  private def replyMsg[ReplyContent: EncodeJson](
    idents: List[Seq[Byte]],
    msgType: String,
    content: ReplyContent,
    metadata: Map[String, String]
  ): Message =
    ParsedMessage(idents, replyHeader(msgType), Some(header), metadata, content).toMessage

  def publish[PubContent: EncodeJson](
    msgType: String,
    content: PubContent,
    metadata: Map[String, String] = Map.empty,
    ident: String = null
  ): Message =
    replyMsg(List(Option(ident).getOrElse(msgType).getBytes("UTF-8")), msgType, content, metadata)

  def reply[ReplyContent: EncodeJson](
    msgType: String,
    content: ReplyContent,
    metadata: Map[String, String] = Map.empty
  ): Message =
    replyMsg(idents, msgType, content, metadata)

  def toMessage(implicit encode: EncodeJson[Content]): Message =
    Message(
      idents,
      OptimizedPrinter.noSpaces(header.asJson),
      parent_header.fold("{}")(x => OptimizedPrinter.noSpaces(x.asJson)),
      OptimizedPrinter.noSpaces(metadata.asJson),
      OptimizedPrinter.noSpaces(content.asJson)
    )
}