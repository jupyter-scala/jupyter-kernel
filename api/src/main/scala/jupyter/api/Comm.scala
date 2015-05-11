package jupyter.api

sealed trait CommChannelMessage

case class CommOpen(target: String, data: String) extends CommChannelMessage
case class CommMessage(data: String) extends CommChannelMessage
case class CommClose(data: String) extends CommChannelMessage

trait Comm[T] { self =>
  def id: NbUUID

  def send(msg: CommChannelMessage)(implicit t: T): Unit

  def onMessage(f: CommChannelMessage => Unit)(implicit t: T): Unit
  def onSentMessage(f: CommChannelMessage => Unit)(implicit t: T): Unit


  def contramap[U](f: U => T): Comm[U] =
    new Comm[U] {
      def id = self.id
      def send(msg: CommChannelMessage)(implicit u: U) = self.send(msg)(f(u))

      def onMessage(f0: CommChannelMessage => Unit)(implicit u: U) = self.onMessage(f0)(f(u))
      def onSentMessage(f0: CommChannelMessage => Unit)(implicit u: U) = self.onSentMessage(f0)(f(u))
    }
}
