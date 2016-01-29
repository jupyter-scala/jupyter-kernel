package jupyter
package kernel
package stream
package zmq

import java.nio.file.Files
import java.util.UUID
import java.io.File
import java.net.{ ServerSocket, InetAddress }
import java.util.concurrent.ExecutorService

import com.typesafe.scalalogging.slf4j.LazyLogging

import jupyter.kernel.protocol._

import scalaz.\/

import scala.sys.process._

import argonaut._, Argonaut._, Shapeless._


object ZMQKernel extends LazyLogging {
  def newConnection(): Connection = {
    val key = UUID.randomUUID().toString
    val ip = {
      val s = InetAddress.getLocalHost.toString
      val idx = s.lastIndexOf('/')
      if (idx < 0)
        s
      else
        s.substring(idx + 1)
    }

    val signatureScheme = "hmac-sha256"
    val transport = "tcp"

    def randomPort(): Int = {
      val s = new ServerSocket(0)
      val p = s.getLocalPort
      s.close()
      p
    }

    val publishPort = randomPort()
    val requestPort = randomPort()
    val controlPort = randomPort()
    val stdinPort = randomPort()
    val heartBeatPort = randomPort()

    Connection(
      ip,
      transport,
      stdinPort,
      controlPort,
      heartBeatPort,
      publishPort,
      requestPort,
      key,
      Some(signatureScheme)
    )
  }

  def writeConnection(connection: Connection, connectionFile: File) = {
    logger.debug(s"Writing $connection to ${connectionFile.getAbsolutePath}")

    connectionFile.getParentFile.mkdirs()
    Files.write(connectionFile.toPath, connection.asJson.spaces2.getBytes()) // default charset
  }

  def apply(
    kernelId: String,
    metaCommand: Seq[String],
    connectionsDir: File
  )(implicit
    pool: ExecutorService
  ): StreamKernel = {

    def launchKernel(connectionFile: File): Unit = {
      val path = connectionFile.getAbsolutePath
      val cmd = metaCommand.map(_.replaceAllLiterally("{connection_file}", path))

      logger.debug(s"Running command $cmd")
      cmd.run()
    }

    StreamKernel.from {
      for {
        x <- \/.fromTryCatchNonFatal {
          val connectionFile = new File(connectionsDir, s"kernel-${UUID.randomUUID()}.json")
          val c = newConnection()
          writeConnection(c, connectionFile)
          (c, connectionFile)
        }
        _ <- \/.fromTryCatchNonFatal(launchKernel(x._2))
        streams <- \/.fromTryCatchNonFatal(ZMQStreams(x._1, isServer = true, identity = Some(kernelId)))
      } yield streams
    }
  }
}
