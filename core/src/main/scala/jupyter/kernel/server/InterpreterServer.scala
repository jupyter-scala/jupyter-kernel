package jupyter
package kernel
package server

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import argonaut.{Json, Parse}

import scala.collection.mutable
import com.typesafe.scalalogging.slf4j.LazyLogging
import interpreter.{Interpreter, InterpreterHandler}
import jupyter.api._
import jupyter.kernel.stream.Streams
import jupyter.kernel.protocol.{ Publish => PublishMsg, Comm => ProtocolComm, _ }
import Formats._

import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async
import scalaz.stream.async.mutable.Queue
import scalaz.{-\/, \/, \/-}

object InterpreterServer extends LazyLogging {

  private class CommImpl(pubQueue: Queue[Message], val id: String, handler: String => Option[CommChannelMessage => Unit]) extends Comm[ParsedMessage[_]] {

    private var target0 = Option.empty[String]

    def received(msg: CommChannelMessage) = {

      msg match {
        case CommOpen(target, _) =>
          target0 = Some(target).filter(_.nonEmpty)

          for  {
            t <- target0
            h <- handler(t)
          }
            messageHandlers = messageHandlers :+ h

        case _ =>
      }

      messageHandlers.foreach(_(msg))
    }

    def send(msg: CommChannelMessage)(implicit t: ParsedMessage[_]) = {
      def parse(s: String): Json =
        Parse.parse(s).leftMap(err => throw new IllegalArgumentException(s"Malformed JSON: $s ($err)")).merge

      pubQueue.enqueueOne(msg match {
        case CommOpen(target, data) =>
          t.publish("comm_open", ProtocolComm.Open(id, target, parse(data)))
        case CommMessage(data) =>
          t.publish("comm_msg", ProtocolComm.Message(id, parse(data)))
        case CommClose(data) =>
          t.publish("comm_close", ProtocolComm.Close(id, parse(data)))
      }).run

      sentMessageHandlers.foreach(_(msg))
    }

    var sentMessageHandlers = Seq.empty[CommChannelMessage => Unit]
    var messageHandlers = Seq.empty[CommChannelMessage => Unit]

    def onMessage(f: CommChannelMessage => Unit) =
      messageHandlers = messageHandlers :+ f
    def onSentMessage(f: CommChannelMessage => Unit) =
      sentMessageHandlers = sentMessageHandlers :+ f
  }

  def apply(
    streams: Streams,
    connectReply: ShellReply.Connect,
    interpreter: Interpreter
  )(implicit
    es: ExecutorService
  ): Task[Unit] = {

    implicit val strategy = Strategy.Executor

    val queues = Channel.channels.map { channel =>
      channel -> async.boundedQueue[Message]()
    }.toMap

    val pubQueue = queues(Channel.Publish)

    val targetHandlers = new ConcurrentHashMap[String, CommChannelMessage => Unit]

    val comms = new mutable.HashMap[String, CommImpl]

    def comm(id: String) = comms.getOrElseUpdate(
      id,
      new CommImpl(pubQueue, id, t => Option(targetHandlers.get(t)))
    )

    val publish = new Publish[ParsedMessage[_]] {
      def stdout(text: String)(implicit t: ParsedMessage[_]) =
        pubQueue.enqueueOne(t.publish("stream", PublishMsg.Stream(name = "stdout", text = text), ident = "stdout")).run
      def stderr(text: String)(implicit t: ParsedMessage[_]) =
        pubQueue.enqueueOne(t.publish("stream", PublishMsg.Stream(name = "stderr", text = text), ident = "stderr")).run
      def display(items: (String, String)*)(implicit t: ParsedMessage[_]) =
        pubQueue.enqueueOne(t.publish("display_data", PublishMsg.DisplayData(items.toMap.mapValues(Json.jString), Map.empty))).run

      def comm(id: String) = comm(id)

      def commHandler(target: String)(handler: CommChannelMessage => Unit) =
        targetHandlers.put(target, handler)
    }

    interpreter.publish(publish)

    val process: (String \/ Message) => Task[Unit] = {
      case -\/(err) =>
        logger.debug(s"Error while decoding message: $err")
        Task.now(())
      case \/-(msg) =>
        InterpreterHandler(interpreter, connectReply, comm(_).received(_), msg) match {
          case -\/(err) =>
            logger.debug(s"Error while handling message: $err")
            Task.now(())
          case \/-(proc) =>
            proc.evalMap {
              case (channel, m) =>
                queues(channel).enqueueOne(m)
            }.run
        }
    }

    val sendTasks = Channel.channels.map { channel =>
      queues(channel).dequeue.to(streams.processes(channel)._2).run
    }

    val sendInitialStatus = pubQueue.enqueueOne(
      ParsedMessage(
        List("status".getBytes("UTF-8")),
        Header(
          msg_id = UUID.randomUUID().toString,
          username = "scala_kernel",
          session = UUID.randomUUID().toString,
          msg_type = "status",
          version = Protocol.versionStrOpt
        ),
        None,
        Map.empty,
        PublishMsg.Status(PublishMsg.ExecutionState0.Starting)
      ).toMessage
    )

    val processRequestMessages = streams.processes(Channel.Requests)._1.evalMap(process).run
    val processControlMessages = streams.processes(Channel.Control)._1.evalMap(process).run

    Task.gatherUnordered(
      Seq(
        sendInitialStatus,
        processRequestMessages,
        processControlMessages
      ) ++
      sendTasks
    ).map(_ => ())
  }

}
