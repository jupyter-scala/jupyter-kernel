package jupyter.kernel.interpreter

import jupyter.kernel.interpreter.Interpreter.Error
import jupyter.kernel.{Message, Channel}, Channel._
import jupyter.kernel.interpreter.DisplayData.RawData
import jupyter.kernel.protocol.Input.{ExecuteRequest, KernelInfoRequest, ConnectRequest}
import jupyter.kernel.protocol.Output.{ Error => _, _ }
import jupyter.kernel.protocol._
import jupyter.kernel.protocol.NbUUID.randomUUID
import jupyter.kernel.protocol.Formats.{ inputConnectRequestEncodeJson, inputKernelInfoRequestEncodeJson, inputExecuteRequestEncodeJson }
import Interpreter._
import utest._
import scala.util.Random.{ nextInt => randomInt }
import scalaz.Scalaz.ToEitherOps
import scalaz.{-\/, \/}

object InterpreterHandlerTests extends TestSuite {

  val echoInterpreter: Interpreter = new Interpreter {
    def interpret(line: String, output: Option[((String) => Unit, (String) => Unit)], storeHistory: Boolean) =
      if (line.isEmpty) Incomplete
      else {
        if (storeHistory) executionCount += 1
        if (line startsWith "error:") Error(line stripPrefix "error:") else Value(new RawData(line))
      }
    def complete(code: String, pos: Int): (Int, Seq[String]) = (pos, Nil)
    var executionCount = 0
  }

  def parse(m: String \/ (Channel, Message), id: NbUUID): String \/ (Channel, ParsedMessage[_]) =
    m.flatMap{ case (c, msg) =>
      msg.decode.map(m => c -> m.copy(header = m.header.copy(msg_id = id)))
    }

  val connectReply = ConnectReply(randomInt(), randomInt(), randomInt(), randomInt())

  val languageInfo = LanguageInfo("echo", "x-echo", "echo", "text/x-echo")

  val idents = List.empty[String]
  val userName = "user"
  val commonId = randomUUID()
  val version = Some("5.0")

  def assertCmp[T](resp: Seq[T], exp: Seq[T], pos: Int = 0): Unit = {
    (resp.toList, exp.toList) match {
      case (Nil, Nil) =>
      case (r :: rt, e :: et) =>
        try assert(r == e)
        catch { case e: utest.AssertionError =>
          println(s"At pos $pos:")
          throw e
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

  val tests = TestSuite{
    'malformedHeader{
      val response = InterpreterHandler(echoInterpreter, connectReply, languageInfo, Message(idents, "{", "", "{}", "{}")).runLog.run.map(parse(_, commonId))
      assertMatch(response) {
        case IndexedSeq(-\/(_)) =>
      }
    }

    'emptyRequest{
      // FIXME Provide a *real* header
      val response = InterpreterHandler(echoInterpreter, connectReply, languageInfo, Message(idents, "{}", "", "{}", "{}")).runLog.run.map(parse(_, commonId))
      assertMatch(response) {
        case IndexedSeq(-\/(_)) =>
      }
    }

    'connectReply{
      val sessionId = randomUUID()

      val req = ParsedMessage(
        idents, Header(randomUUID(), userName, sessionId, "connect_request", version), None, Map.empty,
        ConnectRequest()
      )

      val expected = Seq(
        (Requests -> ParsedMessage(idents, Header(commonId, userName, sessionId, "connect_reply", version), Some(req.header), Map.empty, connectReply)).right
      )

      val response = InterpreterHandler(echoInterpreter, connectReply, languageInfo, req).runLog.run.map(parse(_, commonId))

      assertCmp(response, expected)
    }

    'kernelInfo{
      val sessionId = randomUUID()

      val req = ParsedMessage(
        idents, Header(randomUUID(), userName, sessionId, "kernel_info_request", version), None, Map.empty,
        KernelInfoRequest()
      )

      val expected = Seq(
        (Requests -> ParsedMessage(idents, Header(commonId, userName, sessionId, "kernel_info_reply", version), Some(req.header), Map.empty, KernelInfoReply("5.0", languageInfo))).right
      )

      val response = InterpreterHandler(echoInterpreter, connectReply, languageInfo, req).runLog.run.map(parse(_, commonId))

      assertCmp(response, expected)
    }

    'execute{
      val sessionId = randomUUID()

      val req = ParsedMessage(
        idents, Header(randomUUID(), userName, sessionId, "execute_request", version), None, Map.empty,
        ExecuteRequest("meh", silent = false, None, Map.empty, allow_stdin = false)
      )

      val expected = Seq(
        (Publish -> ParsedMessage(List("execute_input"), Header(commonId, userName, sessionId, "execute_input", version), Some(req.header), Map.empty, ExecuteInput("meh", 1))).right,
        (Publish -> ParsedMessage(List("status"), Header(commonId, userName, sessionId, "status", version), Some(req.header), Map.empty, Output.Status(ExecutionState.busy))).right,
        (Publish -> ParsedMessage(List("execute_result"), Header(commonId, userName, sessionId, "execute_result", version), Some(req.header), Map.empty, ExecuteResult(1, Map("text/plain" -> "meh")))).right,
        (Requests -> ParsedMessage(idents, Header(commonId, userName, sessionId, "execute_reply", version), Some(req.header), Map.empty, ExecuteOkReply(1))).right,
        (Publish -> ParsedMessage(List("status"), Header(commonId, userName, sessionId, "status", version), Some(req.header), Map.empty, Output.Status(ExecutionState.idle))).right
      )

      val response = InterpreterHandler(echoInterpreter, connectReply, languageInfo, req).map{
        case m =>
          Console.err println s"Got $m"
          m
      }.runLog.run.map(parse(_, commonId))

      assertCmp(response, expected)
    }
  }

}
