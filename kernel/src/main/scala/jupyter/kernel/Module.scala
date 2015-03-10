package jupyter.kernel

trait Module {
  def kernels: Map[String, (Kernel, KernelInfo)]
}
