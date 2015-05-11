package jupyter
package kernel.stream
package zmq

import java.io.File

import argonaut._, Argonaut._
import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.protocol.{ Connection, Formats }, Formats._
import scalaz.\/

class ZMQSharedKernel(connectionFile: File, create: Boolean = false, kernelId: String) extends StreamKernel with LazyLogging {
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

  def apply() =
    for {
      c <- connection
      streams <- \/.fromTryCatchNonFatal(ZMQStreams(c, isServer = true, identity = Some(kernelId)))
    } yield streams
}
