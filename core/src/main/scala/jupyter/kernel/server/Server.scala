package jupyter
package kernel
package server

import java.io.{PrintWriter, File}
import java.lang.management.ManagementFactory
import java.net.{InetAddress, ServerSocket}
import java.util.concurrent.ExecutorService
import argonaut._, Argonaut._
import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.stream.{StreamKernel, Streams}
import jupyter.kernel.stream.zmq.ZMQStreams
import jupyter.kernel.protocol.{Connection, Output, NbUUID, Formats}, Formats._
import interpreter.InterpreterKernel
import scalaz._, Scalaz._

import scalaz.concurrent.Task

object Server extends LazyLogging {
  case class Options(
    // @ExtraName("f") @ExtraName("conn") @HelpMessage("path to IPython's connection file")
    connectionFile: String = "",
    eraseConnectionFile: Boolean = false,
    meta: Boolean = false,
    quiet: Boolean = false
  )

  def newConnectionFile(connFile: File): Connection = {
    def randomPort(): Int = {
      val s = new ServerSocket(0)
      val p = s.getLocalPort
      s.close()
      p
    }

    val c = Connection(
      ip = {
        val s = InetAddress.getLocalHost.toString
        val idx = s lastIndexOf '/'
        if (idx < 0)
          s
        else
          s substring idx + 1
      },
      transport = "tcp",
      stdin_port = randomPort(),
      control_port = randomPort(),
      hb_port = randomPort(),
      shell_port = randomPort(),
      iopub_port = randomPort(),
      key = NbUUID.randomUUID().toString,
      signature_scheme = Some("hmac-sha256")
    )

    val w = new PrintWriter(connFile)
    w write c.asJson.spaces2
    w.close()

    c
  }

  private def pid() = ManagementFactory.getRuntimeMXBean.getName.takeWhile(_ != '@').toInt

  def launch(
    kernel: Kernel,
    streams: Streams,
    connection: Connection,
    classLoader: Option[ClassLoader]
  )(implicit es: ExecutorService): Throwable \/ Task[Unit] =
    kernel match {
      case k: InterpreterKernel =>
        for {
          interpreter <- k.interpreter(classLoader)
        } yield
          InterpreterServer(
            streams,
            Output.ConnectReply(
              shell_port=connection.shell_port,
              iopub_port=connection.iopub_port,
              stdin_port=connection.stdin_port,
              hb_port=connection.hb_port
            ),
            interpreter
          )

      case k: StreamKernel =>
        for {
          kernelStreams <- k(classLoader)
        } yield Streams.connect(streams, kernelStreams)

      case other =>
        -\/(new Exception(s"Unhandled kernel type: $other"))
    }

  def apply(
    kernel: Kernel,
    kernelId: String,
    options: Server.Options = Server.Options(),
    classLoaderOption: Option[ClassLoader] = None
  )(implicit es: ExecutorService): Throwable \/ (File, Task[Unit]) =
    for {
      homeDir <- {
        Option(System getProperty "user.home").filterNot(_.isEmpty).orElse(sys.env.get("HOME").filterNot(_.isEmpty)) toRightDisjunction {
          new Exception(s"Cannot get user home dir, set one in the HOME environment variable")
        }
      }
      connFile = {
        Some(options.connectionFile).filter(_.nonEmpty).getOrElse(s"jupyter-kernel_${pid()}.json") match {
          case path if path contains '/' =>
            new File(path)
          case secure =>
            (new File(homeDir) /: List(".ipython", s"profile_default", "secure", secure))(new File(_, _))
        }
      }
      _ <- {
        logger info s"Connection file: ${connFile.getAbsolutePath}"
        \/-(())
      }
      connection <- {
        if (options.eraseConnectionFile || !connFile.exists()) {
          val c = newConnectionFile(connFile)
          logger info s"Creating ipython connection file ${connFile.getAbsolutePath}"
          \/-(c)
        } else
          io.Source.fromFile(connFile).mkString.decodeEither[Connection].leftMap { err =>
            logger error s"Loading connection file: $err"
            new Exception(s"Error while loading connection file: $err")
          }
      }
      streams <- \/.fromTryCatchNonFatal(ZMQStreams(connection, isServer = false, identity = Some(kernelId))) .leftMap { err =>
        new Exception(s"Unable to open connection: $err", err)
      }
      _ <- {
        if (!options.quiet) Console.err println s"Launching kernel"
        \/-(())
      }
      t <- {
        if (options.meta) \/.fromTryCatchNonFatal(MetaServer(streams, launch(kernel, _, connection, classLoaderOption), kernelId))
        else launch(kernel, streams, connection, classLoaderOption)
      }.leftMap(err => new Exception(s"Launching kernel: $err", err))
    } yield (connFile, t)
}
