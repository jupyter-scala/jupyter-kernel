package jupyter.kernel
package stream

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.slf4j.LazyLogging

import jupyter.kernel.protocol.{ Channel, Connection, HMAC }

import org.zeromq.ZMQ
import org.zeromq.ZMQ.{ Poller, PollItem }

import scalaz.\/
import scalaz.Scalaz.ToEitherOps
import scalaz.concurrent.Task
import scalaz.stream.{ Process, Sink }

object ZMQStreams extends LazyLogging {

  private val delimiter = "<IDS|MSG>"
  private val delimiterBytes: Seq[Byte] = delimiter.getBytes("UTF-8")
  private val pollingDelay = 1000L

  def apply(
    connection: Connection,
    identity: Option[String]
  )(implicit
    pool: ExecutorService
  ): Streams = {
    val ctx = ZMQ.context(1)

    val  requests = ctx.socket(ZMQ.ROUTER)
    val   control = ctx.socket(ZMQ.ROUTER)
    val   publish = ctx.socket(ZMQ.PUB)
    val     stdin = ctx.socket(ZMQ.ROUTER)
    val heartbeat = ctx.socket(ZMQ.REP)

    def uri(port: Int) = s"${connection.transport}://${connection.ip}:$port"

    for (id <- identity) {
      val b = id.getBytes("UTF-8")
      requests.setIdentity(b)
      control.setIdentity(b)
      stdin.setIdentity(b)
    }

    requests.setLinger(1000L)
    control.setLinger(1000L)
    publish.setLinger(1000L)
    stdin.setLinger(1000L)
    heartbeat.setLinger(1000L)

    requests.bind(uri(connection.shell_port))
    control.bind(uri(connection.control_port))
    publish.bind(uri(connection.iopub_port))
    stdin.bind(uri(connection.stdin_port))
    heartbeat.bind(uri(connection.hb_port))


    val hmac = HMAC(connection.key, connection.signature_scheme)

    val closed = new AtomicBoolean()

    val heartBeatThread = new Thread("HeartBeat") {
      override def run() = ZMQ.proxy(heartbeat, heartbeat, null)
    }

    heartBeatThread.start()

    def socket(channel: Channel) = channel match {
      case Channel.Requests => requests
      case Channel.Control => control
      case Channel.Publish => publish
      case Channel.Input => stdin
    }

    def sink(channel: Channel): Sink[Task, Message] = {
      val s = socket(channel)

      val emitOne = Process.emit { msg: Message =>
        Task[Unit] {
          logger.debug(s"Sending $msg on $channel")

          msg.idents.map(_.toArray) foreach { s.send(_, ZMQ.SNDMORE) }
          s.send(delimiterBytes.toArray, ZMQ.SNDMORE)
          s.send(if (connection.key.isEmpty) "" else hmac(msg.header, msg.parentHeader, msg.metaData, msg.content), ZMQ.SNDMORE)
          s.send(msg.header, ZMQ.SNDMORE)
          s.send(msg.parentHeader, ZMQ.SNDMORE)
          s.send(msg.metaData, ZMQ.SNDMORE)
          s.send(msg.content)
        }
      }

      lazy val helper: Sink[Task, Message] =
        if (closed.get())
          Process.halt
        else
          emitOne ++ helper

      helper
    }

    def process(channel: Channel) = {
      val s = socket(channel)

      val poll = Task {
        if (closed.get())
          None
        else {
          logger.debug(s"Polling on $channel... ($this)")

          val pi = new PollItem(s, Poller.POLLIN)
          ZMQ.poll(Array(pi), pollingDelay)

          Some(pi.isReadable)
        }
      }

      val read = Task {
        logger.debug(s"Reading message on $channel... ($connection)")

        def recvIdent(): Seq[Byte] = {
          val m = s.recv()
          logger.debug(s"Received message chunk '$m'")
          m
        }

        def recv(): String = {
          val m = s.recvStr()
          logger.debug(s"Received message chunk '$m'")
          m
        }

        val idents =
          if (connection.key.isEmpty)
            Nil
          else
            Stream.continually(recvIdent())
              .takeWhile(_ != delimiterBytes)
              .toList

        val signature =
          if (connection.key.isEmpty)
            Nil
          else
            recv()

        val header = recv()
        val parentHeader = recv()
        val metaData = recv()
        val content = recv()

        logger.debug(s"Read message ${(idents, signature, header, parentHeader, metaData, content)} on $channel")

        lazy val expectedSignatureOpt = hmac(header, parentHeader, metaData, content)

        if (connection.key.nonEmpty && expectedSignatureOpt != signature)
          s"Invalid HMAC signature, got $signature, expected $expectedSignatureOpt".left
        else
          Message(idents, header, parentHeader, metaData, content).right
      }

      lazy val helper: Process[Task, String \/ Message] =
        Process.await(poll) {
          case None =>
            Process.halt
          case Some(true) =>
            Process.eval(read) ++ helper
          case Some(false) =>
            helper
        }

      helper
    }

    def close() = {
      closed.set(true)
      requests.close()
      control.close()
      publish.close()
      stdin.close()
      heartbeat.close()
      ctx.close()
    }

    val processes = Channel.channels.map { channel =>
      channel -> ((process(channel), sink(channel)))
    }.toMap

    Streams(processes.apply, close)
  }
}
