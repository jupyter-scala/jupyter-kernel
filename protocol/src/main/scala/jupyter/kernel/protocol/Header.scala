package jupyter.kernel.protocol

final case class Header(
  msg_id: String,
  username: String,
  session: String,
  msg_type: String,
  version: Option[String]
)
