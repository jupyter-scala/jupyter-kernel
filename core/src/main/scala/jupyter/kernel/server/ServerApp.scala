package jupyter
package kernel
package server

import java.io.{PrintWriter, File}
import java.util.concurrent.Executors

import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.protocol.{ Kernel => KernelDesc }

import scala.compat.Platform._
import scalaz._

case class ServerAppOptions(
  options: Server.Options = Server.Options(),
  exitOnKeyPress: Boolean = false,
  force: Boolean = false
)

object ServerApp extends LazyLogging {

  def generateKernelSpec(kernelId: String, kernelInfo: KernelInfo, progPath: => String, options: ServerAppOptions = ServerAppOptions(), extraProgArgs: Seq[String] = Nil): Unit = {
    if (options.options.copy(quiet = false) != Server.Options())
      Console.err println s"Warning: ignoring kernel launching options when kernel spec option specified"

    val homeDir =
      Option(System getProperty "user.home").filterNot(_.isEmpty).orElse(sys.env.get("HOME").filterNot(_.isEmpty)) getOrElse {
        Console.err println s"Cannot get user home dir, set one in the HOME environment variable"
        sys exit 1
      }

    val kernelJsonFile = (new File(homeDir) /: List(".ipython", "kernels", kernelId, "kernel.json"))(new File(_, _))

    if (!options.force && kernelJsonFile.exists()) {
      Console.err println s"Error: $kernelJsonFile already exists, force erasing it with --force"
      sys exit 1
    }

    val parentDir = kernelJsonFile.getParentFile
    if (!parentDir.exists() && !parentDir.mkdirs()) {
      if (!options.options.quiet)
        Console.err println s"Warning: cannot create directory $parentDir, attempting to generate kernel spec anyway."
    }

    val conn = KernelDesc(
      List(progPath) ++ extraProgArgs ++ List("--quiet", "--connection-file", "{connection_file}"),
      kernelInfo.name,
      kernelInfo.language
    )

    val connStr = {
      import argonaut._, Argonaut._, Shapeless._
      conn.asJson.spaces2
    }

    // FIXME Encode this properly
    val p = new PrintWriter(kernelJsonFile)
    p.write(connStr)
    p.close()

    if (!options.options.quiet) {
      Console.out println s"Generated $kernelJsonFile\n"
      Console.out println s"Run ipython console with this kernel with\n  ipython console --kernel ${if (kernelId.exists(_.isSpaceChar)) "\"" + kernelId + "\"" else kernelId}\n"
      Console.out println "Use this kernel from IPython notebook, running\n  ipython notebook\nand selecting the \"" + kernelInfo.name + "\" kernel"
    }
  }


  def apply(kernelId: String, kernel: Kernel, kernelInfo: KernelInfo, progPath: => String, options: ServerAppOptions = ServerAppOptions(), extraProgArgs: Seq[String] = Nil): Unit = {
    if (options.options.connectionFile.isEmpty)
      generateKernelSpec(kernelId, kernelInfo, progPath, options, extraProgArgs)
    else
      Server(kernel, kernelId, options.options)(Executors.newCachedThreadPool()) match {
        case -\/(err) =>
          // Why aren't the causes stack traces returned here?
          def helper(err: Throwable, count: Int = 0) {
            logger error s"Launching kernel: $err"
            logger error err.getStackTrace.mkString("", EOL, EOL)

            val cause = err.getCause
            if (cause != null && cause != err)
              helper(cause, count + 1)
          }

          helper(err)

          Console.err println s"Error while launching kernel: $err"

          sys exit 1

        case \/-((connFile, task)) =>
          if (!options.options.quiet) {
            if (options.options.meta)
              Console.err println s"Connect Jove notebook to this kernel with\n  jove-notebook --meta --conn-file ${"\"" + connFile.getAbsolutePath + "\""}"
            else {
              Console.err println s"Connect ipython to this kernel with\n  ipython console --existing ${"\"" + connFile.getAbsolutePath + "\""}"
              Console.err println s"or Jove notebook with\n  jove-notebook --conn-file ${"\"" + connFile.getAbsolutePath + "\""}"
            }
          }

          if (options.exitOnKeyPress) {
            if (!options.options.quiet)
              Console.err println "Press enter to exit."
            Console.in.readLine()
            sys exit 0
          } else
            task.run
      }
  }
}
