import sbt._

object MainJupyterBuild extends JupyterBuild(
  scalaVersionStr = "2.11.6",
  crossScalaVersionsStr = Seq("2.10.5", "2.11.6"),
  base = file(".")
)
