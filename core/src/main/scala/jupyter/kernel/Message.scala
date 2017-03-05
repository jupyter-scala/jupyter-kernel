package jupyter
package kernel

import argonaut._, Argonaut._
import protocol.{ Header, ParsedMessage }
import protocol.Formats.decodeHeader

final case class Message(
  idents: List[Seq[Byte]],
  header: String,
  parentHeader: String,
  metaData: String,
  content: String
) {

  def msgType: Either[String, String] = header.decodeEither[Header].right.map(_.msg_type)

  def decodeAs[T: DecodeJson]: Either[String, ParsedMessage[T]] =
    for {
      header <- header.decodeEither[Header].right
      metaData <- metaData.decodeEither[Map[String, String]].right
      content <- content.decodeEither[T].right
    } yield {
      val parentHeaderOpt = parentHeader.decodeEither[Header].right.toOption
      ParsedMessage(idents, header, parentHeaderOpt, metaData, content)
    }

  class AsHelper[T] {
    def apply[U](f: ParsedMessage[T] => U)(implicit decodeJson: DecodeJson[T]): Either[String, U] =
      decodeAs[T].right.map(f)
  }

  def as[T] = new AsHelper[T]

}
