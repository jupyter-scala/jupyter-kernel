package jupyter
package kernel
package socket
package zmq

import java.io.{PrintWriter, File}
import java.net.{ServerSocket, InetAddress}

import com.typesafe.scalalogging.slf4j.LazyLogging
import protocol._
import argonaut._, Argonaut._, Shapeless._
import scalaz.\/

import scala.sys.process._

object ZMQKernel extends LazyLogging {
  def newConnection(): Connection = {
    val key = NbUUID.randomUUID().toString
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
    connectionFile.getParentFile.mkdirs()

    logger debug s"Writing $connection to ${connectionFile.getAbsolutePath}"

    val w = new PrintWriter(connectionFile)
    w write s"""{
               |  "stdin_port": ${connection.stdin_port},
               |  "ip": "${connection.ip}",
               |  "control_port": ${connection.control_port},
               |  "hb_port": ${connection.hb_port},
               |  "signature_scheme": "${connection.signature_scheme}",
               |  "key": "${connection.key}",
               |  "shell_port": ${connection.shell_port},
               |  "transport": "${connection.transport}",
               |  "iopub_port": ${connection.iopub_port}
               |}
             """.stripMargin
    w.close()
  }

  def apply(kernelId: String, metaCommand: Seq[String], connectionsDir: File): SocketKernel =
    new SocketKernel with LazyLogging {
      private def launchKernel(connectionFile: File): Unit = {
        val path = connectionFile.getAbsolutePath
        val cmd = metaCommand.map(_.replaceAllLiterally("{connection_file}", path))

        logger debug s"Running command $cmd"
        cmd.run()
      }

      def socket(classLoader: Option[ClassLoader]) =
        for {
          x <- \/.fromTryCatchNonFatal {
            val connectionFile = new File(connectionsDir, s"kernel-${NbUUID.randomUUID()}.json")
            val c = newConnection()
            writeConnection(c, connectionFile)
            (c, connectionFile)
          }
          _ <- \/.fromTryCatchNonFatal(launchKernel(x._2))
          socket <- x._1.start(isServer = true, identity = Some(kernelId))
        } yield socket
    }
}
