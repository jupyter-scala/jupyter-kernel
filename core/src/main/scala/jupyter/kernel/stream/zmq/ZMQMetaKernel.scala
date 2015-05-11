package jupyter
package kernel
package stream
package zmq

import java.io.File
import java.util.concurrent.ExecutorService

import com.typesafe.scalalogging.slf4j.LazyLogging
import protocol._, Formats._
import argonaut._, Argonaut._
import scalaz.concurrent.{Task, Strategy}
import scalaz.{\/-, -\/, \/}
import scalaz.stream.{ Process, async }

object ZMQMetaKernel {
  def apply(metaConnectionFile: File, kernelId: String, keepAlive: Boolean)(implicit es: ExecutorService): StreamKernel = new StreamKernel with LazyLogging {
    implicit val s = Strategy.Executor

    val metaStreams: () => (Throwable \/ (Message => Task[Unit], () => Task[Message], () => Unit)) = {
      def helper() =
        for {
          lines <- \/.fromTryCatchNonFatal(scala.io.Source.fromFile(metaConnectionFile).mkString)
          c <- lines.decodeEither[Connection].leftMap(s => new Exception(s"Error while reading ${metaConnectionFile.getAbsolutePath}: $s"))
          _ <- \/.fromTryCatchNonFatal(preStart(metaConnectionFile))
          streams <- \/.fromTryCatchNonFatal(ZMQStreams(c, isServer = true, identity = Some(kernelId)))
        } yield {
          val responsesQueue = async.boundedQueue[Message](10)
          streams.requestMessages.flatMap {
            case -\/(err) =>
              logger warn s"Error while decoding message: $err"
              Process.empty
            case \/-(m) =>
              Process.emit(m)
          }.to(responsesQueue.enqueue).run

          val requestsQueue = async.boundedQueue[Message](10)
          requestsQueue.dequeue.to(streams.requestSink).run

          (requestsQueue.enqueueOne _, {
            val q = toLinkedQueue(responsesQueue.dequeue)
            () => Task(q.take())
          }, if (keepAlive) () => () else streams.stop)
        }

      if (keepAlive) {
        // FIXME If using a lazy val for s, running into a weird scalac error:
        //   java.lang.AssertionError: assertion failed: inconvertible types : BYTE -> BOOL
        var s: Throwable \/ (Message => Task[Unit], () => Task[Message], () => Unit) = null
        () => synchronized {
          if (s == null)
            s = helper()
          s
        }
      } else
        () => helper()
    }

    def preStart(connectionFile: File): Unit = {}

    def apply(classLoader: Option[ClassLoader]) =
      metaStreams() flatMap { case (send, receive, end) =>
        try {
          for {
            _ <- send(ParsedMessage(
              Nil,
              Header(NbUUID.randomUUID(), "", NbUUID.randomUUID() /* FIXME*/, "meta_kernel_start_request", Protocol.versionStrOpt),
              None,
              Map.empty,
              Meta.MetaKernelStartRequest()
            ).toMessage).attemptRun
            rawReply <- receive().attemptRun
            msg <- rawReply.decode.flatMap { msg =>
              msg.content match {
                case r: Meta.MetaKernelStartReply => \/-(r)
                case _ => -\/(s"Unrecognized message: $msg")
              }
            } .leftMap(s => new Exception(s"Decoding message (meta): $s"))
            connection <- \/.fromEither(msg.connection) .leftMap(s => new Exception(s"From kernel (meta): $s"))
            streams <- \/.fromTryCatchNonFatal(ZMQStreams(connection, isServer = true, identity = Some(kernelId)))
          } yield streams
        } finally {
          end()
        }
      }
  }
}

