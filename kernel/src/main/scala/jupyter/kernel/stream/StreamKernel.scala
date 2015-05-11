package jupyter.kernel
package stream

import scalaz.\/

trait StreamKernel extends Kernel {
  def apply(classLoader: Option[ClassLoader]): Throwable \/ KernelStreams
}

object StreamKernel {
  def from(f: Option[ClassLoader] => Throwable \/ KernelStreams) =
    new StreamKernel {
      def apply(classLoader: Option[ClassLoader]) = f(classLoader)
    }
}
