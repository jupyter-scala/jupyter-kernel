package jupyter.kernel
package protocol

import acyclic.file

object Meta {
  case class MetaKernelStartRequest()

  case class MetaKernelStartReply(
    connection: Either[String, Connection]
  )
}

