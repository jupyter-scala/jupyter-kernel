package jupyter.kernel
package stream

import scalaz.\/

trait StreamKernel extends Kernel {
  def apply(): Throwable \/ Streams
}

object StreamKernel {
  def from(f: => Throwable \/ Streams) =
    new StreamKernel {
      def apply() = f
    }
}
