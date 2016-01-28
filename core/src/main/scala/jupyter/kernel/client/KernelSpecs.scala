package jupyter.kernel
package client

import argonaut._, Argonaut._, Shapeless._

import stream.zmq.ZMQKernel

import java.io.File

import scala.io.Source

class KernelSpecs {

  private val lock = new AnyRef
  private var kernels0 = Map.empty[String, (KernelInfo, Kernel)]
  private var defaultKernel0 = Option.empty[String]

  def add(id: String, info: KernelInfo, kernel: Kernel): Unit =
    lock.synchronized {
      if (kernels0.isEmpty && defaultKernel0.isEmpty)
        defaultKernel0 = Some(id)

      kernels0 += id -> (info, kernel)
    }

  def setDefault(id: String): Unit =
    lock.synchronized {
      defaultKernel0 = Some(id)
    }

  def default: Option[String] =
    lock.synchronized {
      lazy val ids0 = kernels0.keys.toVector

      def onlyIdOpt =
        if (ids0.length == 1) Some(ids0.head) else None
      def lastWithPrefixOpt(prefix: String) =
        ids0.filter(_.startsWith(prefix)).sorted.lastOption
      def firstOpt =
        if (ids0.nonEmpty) Some(ids0.min) else None

      defaultKernel0
        .orElse(onlyIdOpt)
        .orElse(lastWithPrefixOpt("scala"))
        .orElse(lastWithPrefixOpt("spark"))
        .orElse(firstOpt)
    }

  def kernel(id: String): Option[Kernel] =
    lock.synchronized {
      kernels0.get(id).map {
        case (_, kernel) => kernel
      }
    }

  def kernelInfo(id: String): Option[KernelInfo] =
    lock.synchronized {
      kernels0.get(id).map {
        case (info, _) => info
      }
    }

  def kernels: Map[String, Kernel] =
    lock.synchronized {
      kernels0.map {
        case (id, (_, kernel)) =>
          id -> kernel
      }
    }

  def kernelsWithInfo: Map[String, (KernelInfo, Kernel)] =
    lock.synchronized {
      kernels0
    }

  private def isWindows: Boolean =
    Option(System.getProperty("os.name")).exists(_ startsWith "Windows")

  def kernelSpecDirectories(): Seq[File] = {

    val homeDirOption = Option(System.getProperty("user.home"))
      .filter(_.nonEmpty)
      .orElse(sys.env.get("HOME").filter(_.nonEmpty))

    val fromHomeDir = homeDirOption.toSeq.map { homeDir =>
      new File(homeDir, ".ipython/kernels")
    }

    val shared =
      if (isWindows)
        // IPython 3 doc (http://ipython.org/ipython-doc/3/development/kernels.html#kernelspecs)
        // says %PROGRAMDATA% instead of APPDATA here
        Option(System.getenv("APPDATA")).toSeq.map { appData =>
          new File(appData, "jupyter/kernels")
        }
      else
        Seq(
          new File("/usr/share/jupyter/kernels"),
          new File("/usr/local/share/jupyter/kernels")
        )

    (fromHomeDir ++ shared).filter(_.isDirectory)
  }

  private val iPythonConnectionDir =
    new File(System.getProperty("user.home"), ".ipython/profile_default/security")

  def loadFromKernelSpecs(): Unit = {
    var kernels = Map.empty[String, (KernelInfo, Kernel)]

    case class Spec(
      argv: List[String],
      display_name: String,
      language: Option[String]
    )

    for {
      dir <- kernelSpecDirectories()
      kernelSpecDir <- Option(dir.listFiles()).getOrElse(Array.empty[File]) if kernelSpecDir.isDirectory
      // Priority is given to the first directory of a given kernel id here
      id = kernelSpecDir.getName if !kernels.contains(id)
      specFile = new File(kernelSpecDir, "kernel.json")
      if specFile.exists()
      spec <- Source.fromFile(specFile).mkString.decodeOption[Spec]
    } {
      val info = KernelInfo(
        spec.display_name,
        spec.language getOrElse ""
      )

      val kernel = ZMQKernel(id, spec.argv, iPythonConnectionDir)

      kernels += id -> (info, kernel)
    }

    // Erasing the previously defined kernels with the same ids here
    kernels0 ++= kernels
  }
}
