package jupyter.api

trait Publish[T] { self =>
  def stdout(text: String)(implicit t: T): Unit
  def stderr(text: String)(implicit t: T): Unit

  def display(source: String, items: (String, String)*)(implicit t: T): Unit

  /**
   * Bidirectional communication channel with the front-end.
   * WIP, doesn't work yet.
   */
  def comm(id: String): Comm[T]


  def contramap[U](f: U => T): Publish[U] =
    new Publish[U] {
      def stdout(text: String)(implicit u: U) = self.stdout(text)(f(u))
      def stderr(text: String)(implicit u: U) = self.stderr(text)(f(u))
      def display(source: String, items: (String, String)*)(implicit u: U) = self.display(source, items: _*)(f(u))
      def comm(id: String) = self.comm(id).contramap(f)
    }
}
