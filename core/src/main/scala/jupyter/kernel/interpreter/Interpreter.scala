package jupyter
package kernel.interpreter

import argonaut.Json
import jupyter.kernel.Kernel
import jupyter.kernel.protocol.NbUUID
import jupyter.kernel.protocol.Output.LanguageInfo

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

trait Interpreter extends InterpreterComm {
  def interpret(line: String, output: Option[(String => Unit, String => Unit)], storeHistory: Boolean): Interpreter.Result
  def complete(code: String, pos: Int): (Int, Seq[String])
  def executionCount: Int
  def languageInfo: LanguageInfo
}

trait InterpreterComm {
  def openReceivedHandler(handler: (NbUUID, String, Json) => Unit): Unit
  def openSentHandler(handler: (NbUUID, String, Json) => Unit): Unit
  def msgReceivedHandler(handler: (NbUUID, Json) => Unit): Unit
  def msgSentHandler(handler: (NbUUID, Json) => Unit): Unit
  def closeReceivedHandler(handler: (NbUUID, Json) => Unit): Unit
  def closeSentHandler(handler: (NbUUID, Json) => Unit): Unit

  def openReceived(id: NbUUID, target: String, data: Json): Unit
  def sendOpen(id: NbUUID, target: String, data: Json): Unit
  def msgReceived(id: NbUUID, data: Json): Unit
  def sendMsg(id: NbUUID, data: Json): Unit
  def closeReceived(id: NbUUID, data: Json): Unit
  def sendClose(id: NbUUID, data: Json): Unit
}

trait InterpreterCommImpl extends InterpreterComm {
  private var openReceivedHandlers = Seq.empty[(NbUUID, String, Json) => Unit]
  private var openSentHandlers = Seq.empty[(NbUUID, String, Json) => Unit]
  private var msgReceivedHandlers = Seq.empty[(NbUUID, Json) => Unit]
  private var msgSentHandlers = Seq.empty[(NbUUID, Json) => Unit]
  private var closeReceivedHandlers = Seq.empty[(NbUUID, Json) => Unit]
  private var closeSentHandlers = Seq.empty[(NbUUID, Json) => Unit]

  def openReceivedHandler(handler: (NbUUID, String, Json) => Unit): Unit =
    openReceivedHandlers = openReceivedHandlers :+ handler
  def openSentHandler(handler: (NbUUID, String, Json) => Unit): Unit =
    openSentHandlers = openSentHandlers :+ handler
  def msgReceivedHandler(handler: (NbUUID, Json) => Unit): Unit =
    msgReceivedHandlers = msgReceivedHandlers :+ handler
  def msgSentHandler(handler: (NbUUID, Json) => Unit): Unit =
    msgSentHandlers = msgSentHandlers :+ handler
  def closeReceivedHandler(handler: (NbUUID, Json) => Unit): Unit =
    closeReceivedHandlers = closeReceivedHandlers :+ handler
  def closeSentHandler(handler: (NbUUID, Json) => Unit): Unit =
    closeSentHandlers = closeSentHandlers :+ handler

  def openReceived(id: NbUUID, target: String, data: Json): Unit =
    openReceivedHandlers.foreach(_(id, target, data))
  def sendOpen(id: NbUUID, target: String, data: Json): Unit =
    openSentHandlers.foreach(_(id, target, data))
  def msgReceived(id: NbUUID, data: Json): Unit =
    msgReceivedHandlers.foreach(_(id, data))
  def sendMsg(id: NbUUID, data: Json): Unit =
    msgSentHandlers.foreach(_(id, data))
  def closeReceived(id: NbUUID, data: Json): Unit =
    closeReceivedHandlers.foreach(_(id, data))
  def sendClose(id: NbUUID, data: Json): Unit =
    closeSentHandlers.foreach(_(id, data))
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
