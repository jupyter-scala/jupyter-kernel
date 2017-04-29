package jupyter
package kernel
package server

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import argonaut.{Json, Parse}

import scala.collection.mutable
import com.typesafe.scalalogging.LazyLogging
import interpreter.{DisplayData, Interpreter, InterpreterHandler}
import jupyter.api._
import jupyter.kernel.stream.Streams
import jupyter.kernel.protocol.{ Publish => PublishMsg, Comm => ProtocolComm, _ }
import Formats._

import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async
import scalaz.stream.async.mutable.Queue

object InterpreterServer extends LazyLogging {

  private class CommImpl(pubQueue: Queue[Message], id: String, handler: String => Option[CommChannelMessage => Unit]) { self =>

    def received(msg: CommChannelMessage) =
      messageHandlers.foreach(_(msg))

    var sentMessageHandlers = Seq.empty[CommChannelMessage => Unit]
    var messageHandlers = Seq.empty[CommChannelMessage => Unit]

    def onMessage(f: CommChannelMessage => Unit) =
      messageHandlers = messageHandlers :+ f

    def comm(t: ParsedMessage[_]): Comm = new Comm {
      def id = self.id
      def send(msg: CommChannelMessage) = {
        def parse(s: String): Json =
          Parse.parse(s).left.map(err => throw new IllegalArgumentException(s"Malformed JSON: $s ($err)")).merge

        pubQueue.enqueueOne(msg match {
          case CommOpen(target, data) =>
            t.publish("comm_open", ProtocolComm.Open(id, target, parse(data)))
          case CommMessage(data) =>
            t.publish("comm_msg", ProtocolComm.Message(id, parse(data)))
          case CommClose(data) =>
            t.publish("comm_close", ProtocolComm.Close(id, parse(data)))
        }).unsafePerformSync

        sentMessageHandlers.foreach(_(msg))
      }
      def onMessage(f: CommChannelMessage => Unit) = self.onMessage(f)
      def onSentMessage(f: CommChannelMessage => Unit) =
        sentMessageHandlers = sentMessageHandlers :+ f
    }
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
      channel -> async.boundedQueue[Message](100)
    }.toMap

    val pubQueue = queues(Channel.Publish)

    val targetHandlers = new ConcurrentHashMap[String, CommChannelMessage => Unit]

    val comms = new mutable.HashMap[String, CommImpl]

    def comm0(id: String) = comms.getOrElseUpdate(
      id,
      new CommImpl(pubQueue, id, t => Option(targetHandlers.get(t)))
    )

    interpreter.publish(t =>
      new Publish {
        def stdout(text: String) =
          pubQueue.enqueueOne(t.publish("stream", PublishMsg.Stream(name = "stdout", text = text), ident = "stdout")).unsafePerformSync
        def stderr(text: String) =
          pubQueue.enqueueOne(t.publish("stream", PublishMsg.Stream(name = "stderr", text = text), ident = "stderr")).unsafePerformSync
        def display(items: (String, String)*) =
          pubQueue.enqueueOne(
            t.publish(
              "display_data",
              PublishMsg.DisplayData(
                items.map { case (tpe, data) => DisplayData(tpe, data).jsonField }.toMap,
                Map.empty
              )
            )
          ).unsafePerformSync

        def comm(id: String) = comm0(id).comm(t)

        def commHandler(target: String)(handler: CommChannelMessage => Unit) =
          targetHandlers.put(target, handler)
      }
    )

    def commReceived(id: String, msg: CommChannelMessage) = {

      msg match {
        case CommOpen(target, _) =>
          val target0 = Some(target).filter(_.nonEmpty)

          for  {
            t <- target0
            h <- Option(targetHandlers.get(t))
          }
            comm0(id).onMessage(h)

        case _ =>
      }

      comm0(id).received(msg)
    }

    val process: Either[String, Message] => Task[Unit] = {
      case Left(err) =>
        logger.debug(s"Error while decoding message: $err")
        Task.now(())
      case Right(msg) =>
        InterpreterHandler(interpreter, connectReply, commReceived, msg) match {
          case Left(err) =>
            logger.error(s"Error while handling message: $err\n$msg")
            Task.now(())
          case Right(proc) =>
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
        PublishMsg.Status(PublishMsg.ExecutionState0.Idle)
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
