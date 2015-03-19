package jupyter
package kernel
package socket

import scalaz.\/
import acyclic.file

trait SocketKernel extends Kernel {
  def socket(classLoader: Option[ClassLoader]): Throwable \/ MessageSocket
}
