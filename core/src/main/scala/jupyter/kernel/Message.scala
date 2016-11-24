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

  def msgType: String \/ String = header.decodeEither[Header].map(_.msg_type)

  def decodeAs[T: DecodeJson]: String \/ ParsedMessage[T] =
    for {
      header <- header.decodeEither[Header]
      parentHeader <- parentHeader.decodeEither[Option[Header]]
      metaData <- metaData.decodeEither[Map[String, String]]
      content <- content.decodeEither[T]
    } yield ParsedMessage(idents, header, parentHeader, metaData, content)

  class AsHelper[T] {
    def apply[U](f: ParsedMessage[T] => U)(implicit decodeJson: DecodeJson[T]): String \/ U =
      decodeAs[T].map(f)
  }

  def as[T] = new AsHelper[T]

}
