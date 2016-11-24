package jupyter
package kernel

import argonaut._, Argonaut._
import protocol.{ Header, ParsedMessage }
import protocol.Formats.decodeHeader

import scalaz.\/

case class Message(
  idents: List[Seq[Byte]],
  header: String,
  parentHeader: String,
  metaData: String,
  content: String
) {

  def msgType: String \/ String = \/.fromEither(header.decodeEither[Header].right.map(_.msg_type))

  def decodeAs[T: DecodeJson]: String \/ ParsedMessage[T] = \/.fromEither(
    for {
      header <- header.decodeEither[Header].right
      parentHeader <- parentHeader.decodeEither[Option[Header]].right
      metaData <- metaData.decodeEither[Map[String, String]].right
      content <- content.decodeEither[T].right
    } yield ParsedMessage(idents, header, parentHeader, metaData, content)
  )

  class AsHelper[T] {
    def apply[U](f: ParsedMessage[T] => U)(implicit decodeJson: DecodeJson[T]): String \/ U =
      decodeAs[T].map(f)
  }

  def as[T] = new AsHelper[T]

}
