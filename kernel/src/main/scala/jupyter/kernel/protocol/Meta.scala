package jupyter.kernel
package protocol

import socket.zmq.Connection

object Meta {
  case class MetaKernelStartRequest()

  case class MetaKernelStartReply(
    connection: Either[String, Connection]
  )
}

