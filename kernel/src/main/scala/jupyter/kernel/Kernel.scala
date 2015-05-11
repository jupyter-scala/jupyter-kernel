package jupyter.kernel

import acyclic.file

case class KernelInfo(
  name: String,
  language: String
)

trait Kernel
