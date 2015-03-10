package jupyter
package kernel.socket
package zmq

import java.io.File

import argonaut._, Argonaut._, Shapeless._
import com.typesafe.scalalogging.slf4j.LazyLogging
import scalaz.\/

class ZMQSharedKernel(connectionFile: File, create: Boolean = false, kernelId: String) extends SocketKernel with LazyLogging {
  import ZMQKernel._

  private lazy val connection =
    if (create)
      \/.fromTryCatchNonFatal {
        val c = newConnection()
        writeConnection(c, connectionFile)
        preStart(connectionFile)
        c
      }
    else
      for {
        lines <- \/.fromTryCatchNonFatal(scala.io.Source.fromFile(connectionFile).getLines().mkString("\n"))
        c <- lines.decodeEither[Connection].leftMap(s => new Exception(s"Error while reading ${connectionFile.getAbsolutePath}: $s"))
        _ <- \/.fromTryCatchNonFatal(preStart(connectionFile))
      } yield c

  def preStart(connectionFile: File): Unit = {}

  def socket(classLoader: Option[ClassLoader]) =
    for {
      c <- connection
      socket <- c.start(isServer = true, identity = Some(kernelId))
    } yield socket
}
