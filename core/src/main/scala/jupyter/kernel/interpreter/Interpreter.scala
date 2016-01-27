package jupyter
package kernel.interpreter

import argonaut.Json
import jupyter.kernel.Kernel
import jupyter.api.Publish
import jupyter.kernel.protocol.Output.LanguageInfo
import jupyter.kernel.protocol.ParsedMessage

import scala.runtime.ScalaRunTime._
import scalaz.\/

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

trait Interpreter {
  def init(): Unit = {}
  def initialized: Boolean = true
  def publish(publish: Publish[ParsedMessage[_]]): Unit = {}

  def interpret(line: String, output: Option[(String => Unit, String => Unit)], storeHistory: Boolean, current: Option[ParsedMessage[_]]): Interpreter.Result
  def complete(code: String, pos: Int): (Int, Seq[String])
  def executionCount: Int

  def languageInfo: LanguageInfo
  def implementation = ("", "")
  def banner = ""
  def resultDisplay = false
}

object Interpreter {
  sealed trait Result
  sealed trait Success extends Result
  sealed trait Failure extends Result

  final case class Value(repr: DisplayData) extends Success
  case object NoValue extends Success

  final case class Exception(name: String, msg: String, stackTrace: List[String], exception: Throwable) extends Failure {
    def traceBack = s"$name: $msg" :: stackTrace.map("    " + _)
  }
  final case class Error(message: String) extends Failure
  case object Incomplete extends Failure
  case object Cancelled extends Failure
}

trait InterpreterKernel extends Kernel {
  def apply(): Throwable \/ Interpreter
}
