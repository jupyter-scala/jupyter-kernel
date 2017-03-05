package jupyter
package kernel
package server

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.lang.management.ManagementFactory
import java.net.{InetAddress, ServerSocket}
import java.util.concurrent.ExecutorService

import argonaut._
import Argonaut._
import com.typesafe.scalalogging.LazyLogging
import jupyter.kernel.stream.Streams
import jupyter.kernel.stream.ZMQStreams
import jupyter.kernel.protocol.{Connection, Formats, ShellReply}
import jupyter.kernel.interpreter.InterpreterKernel
import Formats.{ encodeConnection, decodeConnection }

import scalaz._
import scalaz.concurrent.Task

object Server extends LazyLogging {

  final case class Options(
    connectionFile: String = "",
    eraseConnectionFile: Boolean = false,
    quiet: Boolean = false
  )

  def newConnectionFile(connFile: File): Connection = {
    def randomPort(): Int = {
      val s = new ServerSocket(0)
      try s.getLocalPort
      finally s.close()
    }

    val ip = {
      val s = InetAddress.getLocalHost.toString
      val idx = s.lastIndexOf('/')
      if (idx < 0)
        s
      else
        s.substring(idx + 1)
    }

    val c = Connection(
      ip = ip,
      transport = "tcp",
      stdin_port = randomPort(),
      control_port = randomPort(),
      hb_port = randomPort(),
      shell_port = randomPort(),
      iopub_port = randomPort(),
      key = UUID.randomUUID().toString,
      signature_scheme = Some("hmac-sha256")
    )

    Files.write(connFile.toPath, c.asJson.spaces2.getBytes) // default charset

    c
  }

  private def pid() = ManagementFactory.getRuntimeMXBean.getName.takeWhile(_ != '@').toInt

  def launch(
    kernel: InterpreterKernel,
    streams: Streams,
    connection: Connection,
    classLoader: Option[ClassLoader]
  )(implicit es: ExecutorService): Task[Unit] =
    InterpreterServer(
      streams,
      ShellReply.Connect(
        shell_port = connection.shell_port,
        iopub_port = connection.iopub_port,
        stdin_port = connection.stdin_port,
        hb_port = connection.hb_port
      ),
      kernel()
    )

  def apply(
    kernel: InterpreterKernel,
    kernelId: String,
    options: Server.Options = Server.Options(),
    classLoaderOption: Option[ClassLoader] = None
  )(implicit es: ExecutorService): (File, Task[Unit]) = {

    val homeDir = KernelSpecs.homeDirOption.getOrElse {
      throw new Exception("Cannot get user home dir, set one in the HOME environment variable")
    }

    val connFile =
      Some(options.connectionFile).filter(_.nonEmpty).getOrElse(s"jupyter-kernel_${pid()}.json") match {
        case path if path.contains(File.separatorChar) =>
          new File(path)
        case secure =>
          new File(homeDir, s".ipython/profile_default/secure/$secure")
      }

    logger.info(s"Connection file: ${connFile.getAbsolutePath}")

    val connection =
      if (options.eraseConnectionFile || !connFile.exists()) {
        logger.info(s"Creating ipython connection file ${connFile.getAbsolutePath}")
        connFile.getParentFile.mkdirs()
        newConnectionFile(connFile)
      } else
        new String(Files.readAllBytes(connFile.toPath), "UTF-8").decodeEither[Connection] match {
          case Left(err) =>
            logger.error(s"Loading connection file: $err")
            throw new Exception(s"Error while loading connection file: $err")
          case Right(c) =>
            c
        }

    val streams = ZMQStreams(connection, identity = Some(kernelId))

    if (!options.quiet)
      Console.err.println("Launching kernel")

    val t = launch(kernel, streams, connection, classLoaderOption)

    (connFile, t)
  }
}
