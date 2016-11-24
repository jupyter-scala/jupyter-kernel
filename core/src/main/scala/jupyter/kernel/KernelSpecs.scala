package jupyter.kernel

import java.io.File

object KernelSpecs {

  private lazy val isWindows =
    sys.props
      .get("os.name")
      .exists(_.startsWith("Windows"))

  private lazy val isOSX =
    sys.props
      .get("os.name")
      .toSeq
      .contains("Mac OS X")

  lazy val homeDirOption =
    sys.props
      .get("user.home")
      .filter(_.nonEmpty)
      .orElse(
        sys.env
          .get("HOME")
          .filter(_.nonEmpty)
      )

  def userKernelSpecDirectory: Option[File] =
    if (isWindows)
      sys.env
        .get("APPDATA")
        .map(_ + "/jupyter/kernels")
        .map(new File(_))
    else
      homeDirOption.map { homeDir =>
        val path =
          if (isOSX)
            "Library/Jupyter/kernels"
          else
            ".local/share/jupyter/kernels"

        new File(homeDir + "/" + path)
      }

  def systemKernelSpecDirectories: Seq[File] = {

    val paths =
      if (isWindows)
        sys.env
          .get("PROGRAMDATA")
          .map(_ + "/jupyter/kernels")
          .toSeq
      else
        Seq(
          "/usr/share/jupyter/kernels",
          "/usr/local/share/jupyter/kernels"
        )

    paths.map(new File(_))
  }

}