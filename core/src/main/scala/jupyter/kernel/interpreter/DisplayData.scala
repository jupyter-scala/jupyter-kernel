package jupyter.kernel.interpreter

import argonaut.{Json, Parse}

final case class DisplayData(mimeType: String, data: String) {

  private def hasJsonMimeType: Boolean =
    mimeType == "application/json" ||
      (mimeType.startsWith("application/") && mimeType.endsWith("+json"))

  def jsonField: (String, Json) = {

    def asString = Json.jString(data)
    def asJsonOption = Parse.parse(data).right.toOption

    val json =
      if (hasJsonMimeType)
        asJsonOption.getOrElse(asString)
      else
        asString

    mimeType -> json
  }
}

object DisplayData {

  def text(text: String): DisplayData =
    DisplayData("text/plain", text)

  val empty: DisplayData = text("")
}
