package jupyter.kernel.interpreter

import argonaut.Json

final case class DisplayData(mimeType: String, data: String) {
  def jsonField: (String, Json) =
    mimeType -> Json.jString(data)
}

object DisplayData {

  def text(text: String): DisplayData =
    DisplayData("text/plain", text)

  val empty: DisplayData = text("")
}
