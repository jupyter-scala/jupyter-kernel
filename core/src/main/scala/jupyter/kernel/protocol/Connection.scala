package jupyter.kernel.protocol

case class Connection(
  ip: String,
  transport: String,
  stdin_port: Int,
  control_port: Int,
  hb_port: Int,
  shell_port: Int,
  iopub_port: Int,
  key: String,
  signature_scheme: Option[String]
)
