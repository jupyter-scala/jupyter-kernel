package jupyter.api

sealed trait CommChannelMessage

case class CommOpen(target: String, data: String) extends CommChannelMessage
case class CommMessage(data: String) extends CommChannelMessage
case class CommClose(data: String) extends CommChannelMessage

trait Comm[T] { self =>
  def id: String

  def send(msg: CommChannelMessage)(implicit t: T): Unit

  def onMessage(f: CommChannelMessage => Unit): Unit
  def onSentMessage(f: CommChannelMessage => Unit): Unit


  def contramap[U](f: U => T): Comm[U] =
    new Comm[U] {
      def id = self.id
      def send(msg: CommChannelMessage)(implicit u: U) = self.send(msg)(f(u))

      def onMessage(f0: CommChannelMessage => Unit) = self.onMessage(f0)
      def onSentMessage(f0: CommChannelMessage => Unit) = self.onSentMessage(f0)
    }
}
