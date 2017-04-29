import sbt._
import sbt.Keys._

object Deps {

  def argonaut = "io.argonaut" %% "argonaut" % "6.2-RC2"
  def argonautShapeless = "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M4"
  def jeromq = "org.zeromq" % "jeromq" % "0.3.6"
  def macroParadise = "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
  def scalaLogging = Def.setting {
    scalaBinaryVersion.value match {
      case "2.10" =>
        "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"
      case _ =>
        "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
    }
  }
  def scalazStream = "org.scalaz.stream" %% "scalaz-stream" % "0.8.6a"
  def utest = "com.lihaoyi" %% "utest" % "0.4.4"

}
