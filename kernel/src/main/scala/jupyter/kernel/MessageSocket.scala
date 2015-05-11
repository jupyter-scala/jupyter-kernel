package jupyter
package kernel

import com.typesafe.scalalogging.slf4j.LazyLogging

import scalaz.{-\/, \/, \/-}

import acyclic.file

// FIXME Switch to scalaz-stream
trait MessageSocket {
  import MessageSocket.Channel

  def send(channel: Channel, msg: Message): Unit
  def poll(channel: Channel, millis: Long): Boolean
  def receive(channel: Channel): String \/ Message
  def close(): Unit
}

object MessageSocket extends LazyLogging {
  sealed trait Channel
  object Channel {
    case object   Publish extends Channel
    case object  Requests extends Channel
    case object   Control extends Channel 
    case object     Input extends Channel

    val channels = List(Channel.Requests, Channel.Publish, Channel.Control, Channel.Input)
  }

  def transmit(channel: Channel)(from: MessageSocket, send: Message => Unit) =
    new Thread {
      override def run() = {
        while (true)
          if (from.poll(channel, 1000L))
            from.receive(channel) match {
              case -\/(err) =>
                logger warn s"Error while receiving message on $channel: $err"
              case \/-(m) =>
                send(m)
            }
      }
    }


  def process(socket: MessageSocket, process: Message => Unit): Thread =
    new Thread {
      override def run() =
        try {
          while (true)
            if (socket.poll(Channel.Requests, 1000L))
              socket.receive(Channel.Requests) match {
                case -\/(err) =>
                  logger warn s"Error while receiving message: $err"
                case \/-(m) =>
                  try {
                    process(m)
                  } catch {
                    case e: Exception =>
                      logger debug s"While processing $m: $e"
                      logger debug s"${e.getStackTrace.mkString("\n", "\n", "")}"
                  }
              }
        } finally {
          socket.close()
        }
    }
}
