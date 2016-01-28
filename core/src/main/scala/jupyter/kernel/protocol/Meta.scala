package jupyter.kernel
package protocol

object Meta {

  case class MetaKernelStartRequest()

  case class MetaKernelStartReply(
    connection: Either[String, Connection]
  )

}

