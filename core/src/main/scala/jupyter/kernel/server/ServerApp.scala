package jupyter
package kernel
package server

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.protocol.{ Kernel => KernelDesc }

import scala.compat.Platform._
import scalaz._

case class ServerAppOptions(
  connectionFile: String = "",
  eraseConnectionFile: Boolean = false,
  quiet: Boolean = false,
  exitOnKeyPress: Boolean = false,
  force: Boolean = false,
  noCopy: Boolean = false
) {
  lazy val options = Server.Options(
    connectionFile,
    eraseConnectionFile,
    quiet
  )
}

object ServerApp extends LazyLogging {

  def generateKernelSpec(
    kernelId: String,
    kernelInfo: KernelInfo,
    progPath: String,
    isJar: Boolean,
    options: ServerAppOptions = ServerAppOptions(),
    extraProgArgs: Seq[String] = Nil,
    logos: => Seq[((Int, Int), Array[Byte])] = Nil
  ): Unit = {

    if (options.options.copy(quiet = false) != Server.Options())
      Console.err.println(s"Warning: ignoring kernel launching options when kernel spec option specified")

    val homeDir =
      Option(System getProperty "user.home").filterNot(_.isEmpty).orElse(sys.env.get("HOME").filterNot(_.isEmpty)) getOrElse {
        Console.err.println(s"Cannot get user home dir, set one in the HOME environment variable")
        sys.exit(1)
      }

    val kernelJsonFile = new File(homeDir, s".ipython/kernels/$kernelId/kernel.json")
    val launcherFile = new File(homeDir, s".ipython/kernels/$kernelId/launcher.jar")

    if (!options.force) {
      if (kernelJsonFile.exists()) {
        Console.err.println(s"Error: $kernelJsonFile already exists, force erasing it with --force")
        sys.exit(1)
      }

      if (!options.noCopy && launcherFile.exists()) {
        Console.err.println(s"Error: $launcherFile already exists, force erasing it with --force")
        sys.exit(1)
      }
    }

    val parentDir = kernelJsonFile.getParentFile
    if (!parentDir.exists() && !parentDir.mkdirs()) {
      if (!options.options.quiet)
        Console.err.println(s"Warning: cannot create directory $parentDir, attempting to generate kernel spec anyway.")
    }

    val progPath0 =
      if (options.noCopy)
        progPath
      else {
        if (options.force && launcherFile.exists())
          launcherFile.delete()

        launcherFile.getParentFile.mkdirs()
        Files.copy(new File(progPath).toPath, launcherFile.toPath)
        launcherFile.getAbsolutePath
      }

    val launch =
      if (isJar)
        List(
          // FIXME What if  java  is not in PATH?
          "java",
          // needed by the recent proguarded coursier launchers
          "-noverify",
          "-jar",
          progPath0
        )
      else
        List(progPath0)

    val conn = KernelDesc(
      launch ++ extraProgArgs ++ List("--quiet", "--connection-file", "{connection_file}"),
      kernelInfo.name,
      kernelInfo.language
    )

    val connStr = {
      import argonaut._, Argonaut._, Shapeless._
      conn.asJson.spaces2
    }

    Files.write(kernelJsonFile.toPath, connStr.getBytes()) // using the default charset here

    for (((w, h), b) <- logos) {
      val f = new File(homeDir, s".ipython/kernels/$kernelId/logo-${w}x$h.png")
      if (options.force || !f.exists())
        Files.write(f.toPath, b)
    }

    if (!options.options.quiet)
      println(
        s"""Generated $kernelJsonFile
           |
           |Run ipython console with this kernel with
           |  ipython console --kernel ${if (kernelId.exists(_.isSpaceChar)) "\"" + kernelId + "\"" else kernelId}
           |
           |Use this kernel from IPython notebook, running
           |  ipython notebook
           |and selecting the "${kernelInfo.name}" kernel.
         """.stripMargin
      )
  }


  def apply(
    kernelId: String,
    kernel: Kernel,
    kernelInfo: KernelInfo,
    progPath: => String,
    isJar: Boolean,
    options: ServerAppOptions = ServerAppOptions(),
    extraProgArgs: Seq[String] = Nil,
    logos: => Seq[((Int, Int), Array[Byte])] = Nil
  ): Unit = {

    if (options.options.connectionFile.isEmpty)
      generateKernelSpec(kernelId, kernelInfo, progPath, isJar, options, extraProgArgs, logos)
    else
      Server(kernel, kernelId, options.options)(Executors.newCachedThreadPool()) match {
        case -\/(err) =>
          // Why aren't the causes stack traces returned here?
          def helper(err: Throwable, count: Int = 0) {
            logger.error(s"Launching kernel: $err")
            logger.error(err.getStackTrace.mkString("", EOL, EOL))

            val cause = err.getCause
            if (cause != null && cause != err)
              helper(cause, count + 1)
          }

          helper(err)

          Console.err.println(s"Error while launching kernel: $err")
          sys.exit(1)

        case \/-((connFile, task)) =>
          if (!options.options.quiet)
            Console.err.println(s"Connect ipython to this kernel with\n  ipython console --existing ${"\"" + connFile.getAbsolutePath + "\""}")

          if (options.exitOnKeyPress) {
            if (!options.options.quiet)
              Console.err.println("Press enter to exit.")
            Console.in.readLine()
            sys.exit(0)
          } else
            task.run
      }
  }
}
