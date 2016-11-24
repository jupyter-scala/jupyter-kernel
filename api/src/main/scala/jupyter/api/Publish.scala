package jupyter.api

import java.util.UUID

trait Publish[T] { self =>
  def stdout(text: String)(implicit t: T): Unit
  def stderr(text: String)(implicit t: T): Unit

  def display(items: (String, String)*)(implicit t: T): Unit

  /** Opens a communication channel server -> client */
  def comm(id: String = UUID.randomUUID().toString): Comm[T]

  /** Registers a client -> server message handler */
  def commHandler(target: String)(handler: CommChannelMessage => Unit): Unit


  def contramap[U](f: U => T): Publish[U] =
    new Publish[U] {
      def stdout(text: String)(implicit u: U) = self.stdout(text)(f(u))
      def stderr(text: String)(implicit u: U) = self.stderr(text)(f(u))
      def display(items: (String, String)*)(implicit u: U) = self.display(items: _*)(f(u))
      def comm(id: String) = self.comm(id).contramap(f)
      def commHandler(target: String)(handler: CommChannelMessage => Unit) =
        self.commHandler(target)(handler)
    }
}
