package jupyter.kernel.protocol

case class Kernel(argv: List[String],
                  display_name: String,
                  language: String)
