package jupyter
package kernel
package meta

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

import stream.zmq.ZMQMetaKernel
import server.{ ServerApp, ServerAppOptions }

import caseapp._

case class JupyterMetaKernelApp(
  @Recurse
    options: ServerAppOptions,
  @ExtraName("m")
    metaConnectionFile: String,
  id: String,
  @ExtraName("N")
    name: String,
  @ExtraName("L")
    language: String,
  keepAlive: Boolean,
  setup: Boolean
) extends App {

  // FIXME Shouldn't sbt-pack put this in system property "prog.name"?
  val progName = "jupyter-meta-kernel"

  if (setup) {
    val homeDir = Option(System.getProperty("user.home")).filterNot(_.isEmpty).orElse(sys.env.get("HOME").filterNot(_.isEmpty)).map(new File(_)) getOrElse {
      Console.err.println("Cannot get user home dir, set one in the HOME environment variable")
      sys.exit(1)
    }

    val progPath =
      Option(System.getProperty("prog.home")).filter(_.nonEmpty).map(_ + s"/bin/$progName").getOrElse {
        Console.err.println("Cannot get program home dir, it is likely we are not run through pre-packaged binaries.")
        Console.err.println("Please edit the generated file below, and ensure the first item of the 'argv' list points to the path of this program.")
        progName
      }

    val setUpFile = new File(homeDir, ".ipython/.jupyter-meta-path")
    Files.write(setUpFile.toPath, progPath.getBytes("UTF-8"))

    if (!options.options.quiet)
      Console.err.println(s"Set up jupyter-meta in ${setUpFile.getAbsolutePath}")
  }

  if (metaConnectionFile.isEmpty) {
    if (setup)
      sys.exit(0)
    else {
      Console.err.println(s"Error: no meta-connection file specified")
      sys.exit(1)
    }
  }

  val connFile0 = new File(metaConnectionFile)

  if (!connFile0.exists()) {
    Console.err.println(s"Error: meta-connection file $metaConnectionFile not found")
    sys.exit(1)
  }

  if (!connFile0.isFile) {
    Console.err.println(s"Error: meta-connection file $metaConnectionFile is not a file")
    sys.exit(1)
  }

  implicit val es = Executors.newCachedThreadPool()

  def nonEmptyOrElse(s: String, default: String): String =
    if (s.isEmpty)
      default
    else
      s

  ServerApp(
    id,
    ZMQMetaKernel(connFile0, id, keepAlive),
    KernelInfo(
      nonEmptyOrElse(name, "Kernel"),
      nonEmptyOrElse(language, "scala")
    ),
    progName,
    isJar = false,
    options = options,
    extraProgArgs = Seq(
      "--meta-connection-file",
      connFile0.getAbsolutePath
    )
  )
}

object JupyterMetaKernel extends AppOf[JupyterMetaKernelApp]

