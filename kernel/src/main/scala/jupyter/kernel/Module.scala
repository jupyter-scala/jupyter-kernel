package jupyter.kernel

import acyclic.file

trait Module {
  def kernels: Map[String, (Kernel, KernelInfo)]
}
