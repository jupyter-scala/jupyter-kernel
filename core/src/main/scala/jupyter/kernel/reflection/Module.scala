package jupyter.kernel
package reflection

import acyclic.file

trait Module {
  def kernels: Map[String, (Kernel, KernelInfo)]
}
