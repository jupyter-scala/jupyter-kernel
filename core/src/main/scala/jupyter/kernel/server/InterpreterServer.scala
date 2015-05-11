package jupyter
package kernel
package server

import java.util.concurrent.ExecutorService

import com.typesafe.scalalogging.slf4j.LazyLogging
import interpreter.{InterpreterHandler, Interpreter}
import jupyter.api.{Comm, Publish, NbUUID}
import jupyter.kernel.protocol.InputOutput.CommOpen
import jupyter.kernel.stream.Streams
import protocol._, Formats._, jupyter.kernel.protocol.Output.ConnectReply

import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async

import scalaz.{\/, -\/, \/-}

object InterpreterServer extends LazyLogging {

  def apply(streams: Streams,
            connectReply: ConnectReply,
            interpreter: Interpreter)
           (implicit
            es: ExecutorService): Task[Unit] = {

    implicit val strategy = Strategy.Executor

    val reqQueue = async.boundedQueue[Message]()
    val contQueue = async.boundedQueue[Message]()
    val pubQueue = async.boundedQueue[Message]()
    val stdinQueue = async.boundedQueue[Message]()

    interpreter.publish(new Publish[ParsedMessage[_]] {
      def stdout(text: String)(implicit t: ParsedMessage[_]) =
        t.pub("stream", Output.Stream(name = "stdout", text = text))
      def stderr(text: String)(implicit t: ParsedMessage[_]) =
        t.pub("stream", Output.Stream(name = "stderr", text = text))
      def display(source: String, items: (String, String)*)(implicit t: ParsedMessage[_]) =
        t.pub("display_data", Output.DisplayData(source = source, data = items.toMap, metadata = Map.empty))
      def comm(id: NbUUID) = ???
    })

    val process: (String \/ Message) => Task[Unit] = {
      case -\/(err) =>
        logger debug s"Error while decoding message: $err"
        Task.now(())
      case \/-(msg) =>
        InterpreterHandler(interpreter, connectReply, msg).evalMap {
          case \/-((Channel.Requests, m)) =>
            reqQueue enqueueOne m
          case \/-((Channel.Control, m)) =>
            contQueue enqueueOne m
          case \/-((Channel.Publish, m)) =>
            pubQueue enqueueOne m
          case \/-((Channel.Input, m)) =>
            stdinQueue enqueueOne m
          case -\/(err) =>
            logger debug s"Error while handling message: $err"
            Task.now(())
        }.run
    }

    interpreter.openSentHandler { (id, target, data) =>
      reqQueue enqueueOne ParsedMessage(Nil, ???, None, Map.empty, CommOpen(id, target, data)).toMessage
    }

    Task.gatherUnordered(Seq(
      {
        pubQueue enqueueOne {
          ParsedMessage(
            "status" :: Nil,
            Header(
              msg_id = NbUUID.randomUUID(),
              username = "scala_kernel",
              session = NbUUID.randomUUID(),
              msg_type = "status",
              version = Protocol.versionStrOpt
            ),
            None,
            Map.empty,
            Output.Status(ExecutionState.starting)
          ).toMessage
        }
      },
      reqQueue.dequeue.to(streams.requestSink).run,
      contQueue.dequeue.to(streams.controlSink).run,
      pubQueue.dequeue.to(streams.publishSink).run,
      stdinQueue.dequeue.to(streams.inputSink).run,
      streams.requestMessages.evalMap(process).run,
      streams.controlMessages.evalMap(process).run
    )).map(_ => ())
  }

}
