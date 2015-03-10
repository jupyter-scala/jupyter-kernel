package jupyter
package kernel.interpreter

import bridge.DisplayData

trait Interpreter {
  def interpret(line: String, output: Option[(String => Unit, String => Unit)], storeHistory: Boolean): Interpreter.Result
  def complete(s: String): List[String]
  def executionCount: Int
  def reset(): Unit
  def stop(): Unit
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
