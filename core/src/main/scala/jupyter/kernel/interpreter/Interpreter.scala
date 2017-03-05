package jupyter
package kernel.interpreter

import argonaut.Json
import jupyter.api.Publish
import jupyter.kernel.protocol.{ ParsedMessage, ShellReply }

trait Interpreter {
  def init(): Unit = {}
  def initialized: Boolean = true
  def publish(publish: ParsedMessage[_] => Publish): Unit = {}

  def interpret(
    line: String,
    output: Option[(String => Unit, String => Unit)],
    storeHistory: Boolean,
    current: Option[ParsedMessage[_]]
  ): Interpreter.Result
  def isComplete(code: String): Option[Interpreter.IsComplete] =
    None
  def complete(code: String, pos: Int): (Int, Int, Seq[String]) =
    (pos, pos, Nil)
  def executionCount: Int

  def languageInfo: ShellReply.KernelInfo.LanguageInfo
  def implementation = ("", "")
  def banner = ""
  def resultDisplay = false

  def helpLinks: Seq[(String, String)] = Nil
}

object Interpreter {

  sealed abstract class IsComplete extends Product with Serializable

  object IsComplete {
    case object Complete extends IsComplete
    final case class Incomplete(indent: String) extends IsComplete
    case object Invalid extends IsComplete
  }

  sealed abstract class Result extends Product with Serializable
  sealed abstract class Success extends Result
  sealed abstract class Failure extends Result

  final case class Value(data: Seq[DisplayData]) extends Success {

    lazy val jsonMap: Map[String, Json] =
      data.map(_.jsonField).toMap
  }

  case object NoValue extends Success

  final case class Exception(
    name: String,
    msg: String,
    stackTrace: List[String]
  ) extends Failure {
    def traceBack = s"$name: $msg" :: stackTrace.map("    " + _)
  }

  final case class Error(message: String) extends Failure

  case object Cancelled extends Failure
}
