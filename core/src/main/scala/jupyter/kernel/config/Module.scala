package jupyter.kernel
package config

trait Module {
  def kernels: Map[String, (Kernel, KernelInfo)]
}
