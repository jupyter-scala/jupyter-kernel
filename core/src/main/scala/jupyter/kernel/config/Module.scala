package jupyter.kernel
package config

import acyclic.file

trait Module {
  def kernels: Map[String, (Kernel, KernelInfo)]
}
