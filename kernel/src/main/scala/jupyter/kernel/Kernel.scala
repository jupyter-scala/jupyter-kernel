package jupyter.kernel

case class KernelInfo(
  name: String,
  language: String,
  extensions: List[String]
) {
  def isNotebookFileName(s: String) =
    extensions.exists(s endsWith "." + _)
}

trait Kernel
