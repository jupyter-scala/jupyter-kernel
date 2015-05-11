package jupyter.kernel.interpreter

import jupyter.kernel.{ Message, Channel }, Channel._
import jupyter.kernel.interpreter.DisplayData.RawData
import jupyter.kernel.protocol.Output.{ Error => _, _ }
import jupyter.kernel.protocol._
import jupyter.kernel.protocol.NbUUID.randomUUID
import jupyter.kernel.interpreter.Interpreter._

import scala.util.Random.{ nextInt => randomInt }
import scalaz.Scalaz.ToEitherOps
import scalaz.\/
import argonaut.EncodeJson

import utest._

object Helpers {

  def echoInterpreter(): Interpreter = new Interpreter {
    def interpret(line: String, output: Option[((String) => Unit, (String) => Unit)], storeHistory: Boolean) =
      if (line.isEmpty) Incomplete
      else {
        if (storeHistory) executionCount += 1
        if (line startsWith "error:") Error(line stripPrefix "error:") else Value(new RawData(line))
      }
    def complete(code: String, pos: Int): (Int, Seq[String]) = (pos, Nil)
    var executionCount = 0
    val languageInfo = LanguageInfo("echo", "x-echo", "echo", "text/x-echo")
  }

  def randomConnectReply() = ConnectReply(randomInt(), randomInt(), randomInt(), randomInt())

  def parse(m: String \/ (Channel, Message), id: NbUUID = randomUUID()): String \/ (Channel, ParsedMessage[_]) =
    m.flatMap{ case (c, msg) =>
      msg.decode.map(m => c -> m.copy(header = m.header.copy(msg_id = id)))
    }

  def assertCmp[T](resp: Seq[T], exp: Seq[T], pos: Int = 0): Unit = {
    (resp.toList, exp.toList) match {
      case (Nil, Nil) =>
      case (r :: rt, e :: et) =>
        try assert(r == e)
        catch { case ex: utest.AssertionError =>
          println(s"At pos $pos:")
          println(s"Response: $r")
          println(s"Expected: $e")
          throw ex
        }

        assertCmp(rt, et, pos + 1)

      case (Nil, e :: et) =>
        println(s"At pos $pos, missing $e")
        assert(false)

      case (l @ (r :: rt), Nil) =>
        println(s"At pos $pos, extra item(s) $l")
        assert(false)
    }
  }

  case class Req[T: EncodeJson](msgType: String, t: T) {
    def apply(idents: List[String], userName: String, sessionId: NbUUID, version: Option[String]): (Message, Header) = {
      val msg = ParsedMessage(
        idents, Header(randomUUID(), userName, sessionId, msgType, version), None, Map.empty,
        t
      )

      (msg, msg.header)
    }
  }

  sealed trait Reply {
    def apply(defaultIdents: List[String], userName: String, sessionId: NbUUID, replyId: NbUUID, parHdr: Header, version: Option[String]): String \/ (Channel, ParsedMessage[_])
  }
  case class ReqReply[T](msgType: String, t: T) extends Reply {
    def apply(defaultIdents: List[String], userName: String, sessionId: NbUUID, replyId: NbUUID, parHdr: Header, version: Option[String]) =
      (Requests -> ParsedMessage(defaultIdents, Header(replyId, userName, sessionId, msgType, version), Some(parHdr), Map.empty, t)).right
  }
  case class PubReply[T](idents: List[String], msgType: String, t: T) extends Reply {
    def apply(defaultIdents: List[String], userName: String, sessionId: NbUUID, replyId: NbUUID, parHdr: Header, version: Option[String]) =
      (Publish -> ParsedMessage(idents, Header(replyId, userName, sessionId, msgType, version), Some(parHdr), Map.empty, t)).right
  }

  def session(intp: Interpreter, version: Option[String], connectReply: ConnectReply)(msgs: (Req[_], Seq[Reply])*) = {
    val idents = List.empty[String]
    val userName = "user"
    val sessionId = randomUUID()
    val commonId = randomUUID()
    for (((req, replies), idx) <- msgs.zipWithIndex) {
      val (msg, msgHdr) = req(idents, userName, sessionId, version)
      val expected = replies.map(_(idents, userName, sessionId, commonId, msgHdr, version))
      val response = InterpreterHandler(intp, connectReply, msg).runLog.run.map(parse(_, commonId))
      assertCmp(response, expected)
    }
  }

}
