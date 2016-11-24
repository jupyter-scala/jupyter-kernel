package jupyter.kernel.interpreter

case class DisplayData(mimeType: String, data: String)

object DisplayData {

  def text(text: String): DisplayData =
    DisplayData("text/plain", text)

  val empty: DisplayData = text("")
}
