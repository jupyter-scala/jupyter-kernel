package jupyter.kernel

case class KernelInfo(
  name: String,
  language: String
)

trait Kernel
