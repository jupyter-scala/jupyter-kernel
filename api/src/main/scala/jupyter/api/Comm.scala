package jupyter.api

sealed abstract class CommChannelMessage extends Product with Serializable

case class CommOpen(target: String, data: String) extends CommChannelMessage
case class CommMessage(data: String) extends CommChannelMessage
case class CommClose(data: String) extends CommChannelMessage

trait Comm {
  def id: String

  def send(msg: CommChannelMessage): Unit

  final def open(target: String, data: String): Unit =
    send(CommOpen(target, data))
  final def message(data: String): Unit =
    send(CommMessage(data))
  final def close(data: String): Unit =
    send(CommClose(data))

  def onMessage(f: CommChannelMessage => Unit): Unit
  def onSentMessage(f: CommChannelMessage => Unit): Unit
}
