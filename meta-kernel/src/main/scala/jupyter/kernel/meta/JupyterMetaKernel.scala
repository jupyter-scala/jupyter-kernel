package jupyter
package kernel
package meta

import java.io.{ File, PrintWriter }

import socket.zmq.ZMQMetaKernel
import server.{ ServerApp, ServerAppOptions }

import caseapp._

case class JupyterMetaKernel(
  options: ServerAppOptions,
  @ExtraName("m") metaConnectionFile: String,
  id: String,
  @ExtraName("N") name: String,
  @ExtraName("L") language: String,
  @ExtraName("x") extension: List[String],
  keepAlive: Boolean,
  setup: Boolean
) extends App {

  // FIXME Shouldn't sbt-pack put this in system property "prog.name"?
  val progName = "jupyter-meta-kernel"

  if (setup) {
    val homeDir = Option(System getProperty "user.home").filterNot(_.isEmpty).orElse(sys.env.get("HOME").filterNot(_.isEmpty)).map(new File(_)) getOrElse {
      Console.err println "Cannot get user home dir, set one in the HOME environment variable"
      sys exit 1
    }

    val progPath =
      Option(System getProperty "prog.home").filterNot(_.isEmpty).map(_ + s"/bin/$progName") getOrElse {
        Console.err println "Cannot get program home dir, it is likely we are not run through pre-packaged binaries."
        Console.err println "Please edit the generated file below, and ensure the first item of the 'argv' list points to the path of this program."
        progName
      }

    val setUpFile = (homeDir /: List(".ipython", ".jupyter-meta-path"))(new File(_, _))
    val w = new PrintWriter(setUpFile)
    w write progPath
    w.close()
    if (!options.options.quiet)
      Console.err println s"Set up jupyter-meta in ${setUpFile.getAbsolutePath}"
  }

  if (metaConnectionFile.isEmpty) {
    if (setup) {
      sys exit 0
    } else {
      Console.err println s"Error: no meta-connection file specified"
      sys exit 1
    }
  }

  val _connFile = new File(metaConnectionFile)

  if (!_connFile.exists()) {
    Console.err println s"Error: meta-connection file $metaConnectionFile not found"
    sys exit 1
  }

  if (!_connFile.isFile) {
    Console.err println s"Error: meta-connection file $metaConnectionFile is not a file"
    sys exit 1
  }

  ServerApp(
    id,
    ZMQMetaKernel(_connFile, id, keepAlive),
    KernelInfo(
      Some(name).filter(_.nonEmpty) getOrElse "Kernel",
      Some(language).filter(_.nonEmpty) getOrElse "scala",
      Some(extension).filter(_.nonEmpty) getOrElse List("snb")
    ),
    progName,
    options,
    extraProgArgs = Seq(
      "--meta-connection-file",
      _connFile.getAbsolutePath
    )
  )
}

object JupyterMetaKernel extends AppOf[JupyterMetaKernel] {
  val parser = default
}

