package jupyter.bridge

import scala.runtime.ScalaRunTime.stringOf
import acyclic.file

sealed trait DisplayData {
  def data: Seq[(String, String)]
}

object DisplayData {
  case class UserData(data: Seq[(String, String)]) extends DisplayData
  class RawData(s: String) extends DisplayData {
    def data = Seq("text/plain" -> s)
  }
  object RawData {
    private val maxLength = 1000

    def apply(v: Any): RawData =
      new RawData({
        val s = stringOf(v)

        if (s.length <= maxLength)
          s
        else
          s.take(maxLength) + "…"
      })
  }
  case object EmptyData extends DisplayData {
    def data = Seq("text/plain" -> "")
  }
}
