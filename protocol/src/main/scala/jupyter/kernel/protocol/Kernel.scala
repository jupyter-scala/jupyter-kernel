package jupyter.kernel.protocol

final case class Kernel(
  argv: List[String],
  display_name: String,
  language: String
)
