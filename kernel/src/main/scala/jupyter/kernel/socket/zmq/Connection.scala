package jupyter.kernel.socket.zmq

import scalaz.\/

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
) {
  /** isServer: is *notebook* (or console) server */
  def start(isServer: Boolean, identity: Option[String]): Throwable \/ ZMQMessageSocket = \/.fromTryCatchNonFatal {
    new ZMQMessageSocket(
      key,
      signature_scheme,
      transport,
      ip,
      iopub_port,
      shell_port,
      control_port,
      stdin_port,
      hb_port,
      isServer = isServer,
      identity = identity
    )
  }
}
