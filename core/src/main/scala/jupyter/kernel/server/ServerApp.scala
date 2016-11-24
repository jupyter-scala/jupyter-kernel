package jupyter
package kernel
package server

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

import com.typesafe.scalalogging.slf4j.LazyLogging
import jupyter.kernel.interpreter.InterpreterKernel
import jupyter.kernel.protocol.{ Kernel => KernelDesc }

import scala.compat.Platform._
import scala.util.{ Failure, Success, Try }

case class ServerAppOptions(
  connectionFile: String = "",
  eraseConnectionFile: Boolean = false,
  quiet: Boolean = false,
  exitOnKeyPress: Boolean = false,
  force: Boolean = false,
  noCopy: Boolean = false,
  jupyterPath: String = "",
  global: Boolean = false
) {
  lazy val options = Server.Options(
    connectionFile,
    eraseConnectionFile,
    quiet
  )
}

object ServerApp extends LazyLogging {

  def generateKernelSpec(
    id: String,
    name: String,
    language: String,
    progPath: String,
    isJar: Boolean,
    options: ServerAppOptions = ServerAppOptions(),
    extraProgArgs: Seq[String] = Nil,
    logos: => Seq[((Int, Int), Array[Byte])] = Nil
  ): Unit = {

    if (options.options.copy(quiet = false) != Server.Options())
      Console.err.println(s"Warning: ignoring kernel launching options when kernel spec option specified")

    val kernelsDir =
      if (options.jupyterPath.isEmpty) {

        // error message suck... can it fail anyway?

        if (options.global)
          KernelSpecs.systemKernelSpecDirectories.headOption.getOrElse {
            Console.err.println("No global kernel directory found")
            sys.exit(1)
          }
        else
          KernelSpecs.userKernelSpecDirectory.getOrElse {
            Console.err.println("No user kernel directory found")
            sys.exit(1)
          }
      } else
        new File(options.jupyterPath + "/kernels")

    val kernelDir = new File(kernelsDir, id)

    val kernelJsonFile = new File(kernelDir, "kernel.json")
    val launcherFile = new File(kernelDir, "launcher.jar")

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

    if (!parentDir.exists() && !parentDir.mkdirs() && !options.options.quiet)
      Console.err.println(
        s"Warning: cannot create directory $parentDir, attempting to generate kernel spec anyway."
      )

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
      name,
      language
    )

    val connStr = {
      import argonaut._, Argonaut._, Shapeless._
      conn.asJson.spaces2
    }

    Files.write(kernelJsonFile.toPath, connStr.getBytes()) // using the default charset here

    for (((w, h), b) <- logos) {
      val f = new File(kernelDir, s"logo-${w}x$h.png")
      if (options.force || !f.exists())
        Files.write(f.toPath, b)
    }

    if (!options.options.quiet) {
      val kernelId0 =
        if (id.exists(_.isSpaceChar))
          "\"" + id + "\""
        else
          id

      println(
        s"""Generated $kernelJsonFile
           |
           |Run jupyter console with this kernel with
           |  jupyter console --kernel $kernelId0
           |
           |Use this kernel from Jupyter notebook, running
           |  jupyter notebook
           |and selecting the "$name" kernel.
         """.stripMargin
      )
    }
  }

  def apply(
    id: String,
    name: String,
    language: String,
    kernel: InterpreterKernel,
    progPath: => String,
    isJar: Boolean,
    options: ServerAppOptions = ServerAppOptions(),
    extraProgArgs: Seq[String] = Nil,
    logos: => Seq[((Int, Int), Array[Byte])] = Nil
  ): Unit =
    if (options.options.connectionFile.isEmpty)
      generateKernelSpec(id, name, language, progPath, isJar, options, extraProgArgs, logos)
    else
      Try(Server(kernel, id, options.options)(Executors.newCachedThreadPool())) match {
        case Failure(err) =>
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

        case Success((connFile, task)) =>
          if (!options.options.quiet)
            Console.err.println(
              s"""Connect jupyter to this kernel with
                 |  jupyter console --existing "${connFile.getAbsolutePath}"
               """.stripMargin
            )

          if (options.exitOnKeyPress) {
            if (!options.options.quiet)
              Console.err.println("Press enter to exit.")
            Console.in.readLine()
            sys.exit(0)
          } else
            task.run
      }
}
