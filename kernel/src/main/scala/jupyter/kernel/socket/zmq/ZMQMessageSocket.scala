package jupyter
package kernel
package socket.zmq

import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.protocol.{Connection, HMAC}

import scalaz.{\/-, -\/, \/}
import MessageSocket._
import org.zeromq.ZMQ
import org.zeromq.ZMQ.{ Poller, PollItem }

import acyclic.file

case class ZMQMessageSocket(
  key: String,
  signatureScheme: Option[String],
  transport: String,
  ip: String,
  iopubPort: Int,
  shellPort: Int,
  controlPort: Int,
  stdinPort: Int,
  heartbeatPort: Int,
  isServer: Boolean,
  identity: Option[String] = None
) extends MessageSocket with LazyLogging {
  private val DELIMITER = "<IDS|MSG>"

  private val ctx = ZMQ.context(1)

  private val   publish = ctx socket (if (isServer) ZMQ.SUB else ZMQ.PUB)
  private val  requests = ctx socket (if (isServer) ZMQ.DEALER else ZMQ.ROUTER)
  private val   control = ctx socket (if (isServer) ZMQ.DEALER else ZMQ.ROUTER)
  private val     stdin = ctx socket (if (isServer) ZMQ.DEALER else ZMQ.ROUTER)
  private val heartbeat = ctx socket (if (isServer) ZMQ.REQ else ZMQ.REP)

  private def toURI(port: Int) = s"$transport://$ip:$port"

  for (id <- identity) {
    val _id = id.getBytes
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
    publish connect toURI(iopubPort)
    publish subscribe Array.empty
    requests connect toURI(shellPort)
    control connect toURI(controlPort)
    stdin connect toURI(stdinPort)
    heartbeat connect toURI(heartbeatPort)
  } else {
    publish bind toURI(iopubPort)
    requests bind toURI(shellPort)
    control bind toURI(controlPort)
    stdin bind toURI(stdinPort)
    heartbeat bind toURI(heartbeatPort)
  }


  private val hmac = HMAC(key, signatureScheme)

  private val _lock = new AnyRef
  private var _heartBeatThread: Thread = null

  def startHeartBeat(): Unit = _lock.synchronized {
    if (_heartBeatThread == null) {
      _heartBeatThread = new Thread {
        override def run() = ZMQ.proxy(heartbeat, heartbeat, null)
      }
      _heartBeatThread setName "HeartBeat"
      _heartBeatThread.start()
    }
  }

  private def channelZmqSocket(channel: Channel) = channel match {
    case Channel.Publish => publish
    case Channel.Requests => requests
    case Channel.Control => control
    case Channel.Input => stdin
  }

  def send(channel: Channel, msg: Message): Unit = {
    val zmqSocket = channelZmqSocket(channel)

    logger debug s"Sending message $msg on $channel ($this)"

    zmqSocket.synchronized {
      msg.idents foreach { zmqSocket.send(_, ZMQ.SNDMORE) }
      zmqSocket.send(DELIMITER, ZMQ.SNDMORE)
      zmqSocket.send(if (key.isEmpty) "" else hmac(msg.header, msg.parentHeader, msg.metaData, msg.content), ZMQ.SNDMORE)
      zmqSocket.send(msg.header, ZMQ.SNDMORE)
      zmqSocket.send(msg.parentHeader, ZMQ.SNDMORE)
      zmqSocket.send(msg.metaData, ZMQ.SNDMORE)
      zmqSocket.send(msg.content)
    }
  }

  def poll(channel: Channel, millis: Long): Boolean = {
    val zmqSocket = channelZmqSocket(channel)
    val pi = Array(new PollItem(zmqSocket, Poller.POLLIN))

    logger debug s"Polling on $channel... ($this)"

    ZMQ.poll(pi, millis)
    pi(0).isReadable
  }

  def receive(channel: Channel): String \/ Message = {
    val zmqSocket = channelZmqSocket(channel)

    logger debug s"Reading message on $channel... ($this)"

    def recv(): String = {
      val s = zmqSocket.recvStr()
      logger debug s"Received message chunk '$s'"
      s
    }

    val (idents, signature, header, parentHeader, metaData, content) = zmqSocket.synchronized {(
      if (key.nonEmpty) Stream.continually(recv()).takeWhile(_ != DELIMITER).toList else Nil,
      if (key.nonEmpty) recv() else Nil,
      recv(),
      recv(),
      recv(),
      recv()
    )}

    logger debug s"Read message ${(idents, signature, header, parentHeader, metaData, content)} on $channel"

    lazy val expectedSignatureOpt = hmac(header, parentHeader, metaData, content)

    if (key.nonEmpty && expectedSignatureOpt != signature)
      -\/(s"Invalid HMAC signature, got $signature, expected $expectedSignatureOpt")
    else
      \/-(Message(idents, header, parentHeader, metaData, content))
  }

  def join(): Unit =
    _lock.synchronized(_heartBeatThread).join()

  def close(): Unit = {
    publish.close()
    requests.close()
    control.close()
    stdin.close()
    heartbeat.close()

    ctx.term()
  }
}

object ZMQMessageSocket {
  def start(connection: Connection, isServer: Boolean, identity: Option[String]): Throwable \/ ZMQMessageSocket = \/.fromTryCatchNonFatal {
    new ZMQMessageSocket(
      connection.key,
      connection.signature_scheme,
      connection.transport,
      connection.ip,
      connection.iopub_port,
      connection.shell_port,
      connection.control_port,
      connection.stdin_port,
      connection.hb_port,
      isServer = isServer,
      identity = identity
    )
  }
}
