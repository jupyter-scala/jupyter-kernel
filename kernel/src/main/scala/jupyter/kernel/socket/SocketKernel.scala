package jupyter
package kernel
package socket

import scalaz.\/

trait SocketKernel extends Kernel {
  def socket(classLoader: Option[ClassLoader]): Throwable \/ MessageSocket
}
