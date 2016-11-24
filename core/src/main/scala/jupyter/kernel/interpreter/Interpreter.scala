package jupyter
package kernel.interpreter

import jupyter.api.Publish
import jupyter.kernel.protocol.{ ParsedMessage, ShellReply }

trait Interpreter {
  def init(): Unit = {}
  def initialized: Boolean = true
  def publish(publish: Publish[ParsedMessage[_]]): Unit = {}

  def interpret(
    line: String,
    output: Option[(String => Unit, String => Unit)],
    storeHistory: Boolean,
    current: Option[ParsedMessage[_]]
  ): Interpreter.Result
  def complete(code: String, pos: Int): (Int, Int, Seq[String])
  def executionCount: Int

  def languageInfo: ShellReply.KernelInfo.LanguageInfo
  def implementation = ("", "")
  def banner = ""
  def resultDisplay = false

  def helpLinks: Seq[(String, String)] = Nil
}

object Interpreter {

  sealed abstract class Result extends Product with Serializable
  sealed abstract class Success extends Result
  sealed abstract class Failure extends Result

  case class Value(data: Seq[DisplayData]) extends Success {

    lazy val map: Map[String, String] =
      data.map {
        case DisplayData(mime, value) => mime -> value
      }.toMap
  }

  case object NoValue extends Success

  case class Exception(
    name: String,
    msg: String,
    stackTrace: List[String]
  ) extends Failure {
    def traceBack = s"$name: $msg" :: stackTrace.map("    " + _)
  }

  case class Error(message: String) extends Failure

  case object Incomplete extends Failure
  case object Cancelled extends Failure
}
