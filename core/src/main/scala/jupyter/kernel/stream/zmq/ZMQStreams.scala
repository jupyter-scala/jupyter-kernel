package jupyter.kernel
package stream
package zmq

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.protocol.{ HMAC, Connection }
import org.zeromq.ZMQ
import org.zeromq.ZMQ.{ Poller, PollItem }

import scalaz.{ -\/, \/, \/- }
import scalaz.concurrent.Task
import scalaz.stream.{ Process, Sink }

object ZMQStreams extends LazyLogging {

  private val delimiter = "<IDS|MSG>"
  private val delimiterBytes: Seq[Byte] = delimiter.getBytes("UTF-8")
  private val pollingDelay = 1000L

  def apply(connection: Connection, isServer: Boolean, identity: Option[String]): Streams = {
    val ctx = ZMQ.context(1)

    val   publish = ctx socket (if (isServer) ZMQ.SUB else ZMQ.PUB)
    val  requests = ctx socket (if (isServer) ZMQ.DEALER else ZMQ.ROUTER)
    val   control = ctx socket (if (isServer) ZMQ.DEALER else ZMQ.ROUTER)
    val     stdin = ctx socket (if (isServer) ZMQ.DEALER else ZMQ.ROUTER)
    val heartbeat = ctx socket (if (isServer) ZMQ.REQ else ZMQ.REP)

    def toURI(port: Int) = s"${connection.transport}://${connection.ip}:$port"

    for (id <- identity) {
      val _id = id.getBytes("UTF-8")
      requests setIdentity _id
      control setIdentity _id
      stdin setIdentity _id
    }

    publish setLinger 1000L
    requests setLinger 1000L
    control setLinger 1000L
    stdin setLinger 1000L
    heartbeat setLinger 1000L

    if (isServer) {
      publish connect toURI(connection.iopub_port)
      publish subscribe Array.empty
      requests connect toURI(connection.shell_port)
      control connect toURI(connection.control_port)
      stdin connect toURI(connection.stdin_port)
      heartbeat connect toURI(connection.hb_port)
    } else {
      publish bind toURI(connection.iopub_port)
      requests bind toURI(connection.shell_port)
      control bind toURI(connection.control_port)
      stdin bind toURI(connection.stdin_port)
      heartbeat bind toURI(connection.hb_port)
    }


    val hmac = HMAC(connection.key, connection.signature_scheme)

    val closed = new AtomicBoolean()

    val _lock = new AnyRef
    var _heartBeatThread: Thread = null

    def startHeartBeat(): Unit = _lock.synchronized {
      if (_heartBeatThread == null) {
        _heartBeatThread = new Thread {
          override def run() = ZMQ.proxy(heartbeat, heartbeat, null)
        }
        _heartBeatThread setName "HeartBeat"
        _heartBeatThread.start()
      }
    }

    startHeartBeat()

    def channelZmqSocket(channel: Channel) = channel match {
      case Channel.Publish => publish
      case Channel.Requests => requests
      case Channel.Control => control
      case Channel.Input => stdin
    }

    def sink(channel: Channel): Sink[Task, Message] = {
      val s = channelZmqSocket(channel)

      def helper(): Sink[Task, Message] =
        if (closed.get())
          Process.halt
        else
          Process.emit { msg: Message =>
            logger debug s"Sending $msg on $channel"

            Task[Unit] {
              msg.idents.map(_.toArray) foreach { s.send(_, ZMQ.SNDMORE) }
              s.send(delimiterBytes.toArray, ZMQ.SNDMORE)
              s.send(if (connection.key.isEmpty) "" else hmac(msg.header, msg.parentHeader, msg.metaData, msg.content), ZMQ.SNDMORE)
              s.send(msg.header, ZMQ.SNDMORE)
              s.send(msg.parentHeader, ZMQ.SNDMORE)
              s.send(msg.metaData, ZMQ.SNDMORE)
              s.send(msg.content)
            }
          } ++ helper()

      helper()
    }

    def process(channel: Channel) = {
      val s = channelZmqSocket(channel)

      def poll() = Task {
        val pi = Array(new PollItem(s, Poller.POLLIN))

        logger debug s"Polling on $channel... ($this)"

        ZMQ.poll(pi, pollingDelay)
        pi(0).isReadable
      }

      def read() = Task {
        logger debug s"Reading message on $channel... ($connection)"

        def recvIdent(): Seq[Byte] = {
          val m = s.recv()
          logger debug s"Received message chunk '$m'"
          m
        }

        def recv(): String = {
          val m = s.recvStr()
          logger debug s"Received message chunk '$m'"
          m
        }

        val (idents, signature, header, parentHeader, metaData, content) = (
          if (connection.key.nonEmpty) Stream.continually(recvIdent()).takeWhile(_ != delimiterBytes).toList else Nil,
          if (connection.key.nonEmpty) recv() else Nil,
          recv(),
          recv(),
          recv(),
          recv()
        )

        logger debug s"Read message ${(idents, signature, header, parentHeader, metaData, content)} on $channel"

        lazy val expectedSignatureOpt = hmac(header, parentHeader, metaData, content)

        if (connection.key.nonEmpty && expectedSignatureOpt != signature)
          -\/(s"Invalid HMAC signature, got $signature, expected $expectedSignatureOpt")
        else
          \/-(Message(idents, header, parentHeader, metaData, content))
      }

      def helper(): Process[Task, String \/ Message] =
        if (closed.get())
          Process.halt
        else
          Process.await(poll()) {
            case true =>
              if (closed.get())
                Process.halt
              else
                Process.eval(read()) ++ helper()
            case false =>
              helper()
          }

      helper()
    }

    def close() = {
      closed set true
      publish.close()
      requests.close()
      control.close()
      stdin.close()
      heartbeat.close()
      ctx.close()
    }

    Streams(
      process(Channel.Requests), sink(Channel.Requests),
      process(Channel.Control), sink(Channel.Control),
      process(Channel.Publish), sink(Channel.Publish),
      process(Channel.Input), sink(Channel.Input),
      close
    )
  }
}
