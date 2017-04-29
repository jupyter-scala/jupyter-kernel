import sbt._
import sbt.Keys._

import Aliases._

object Settings {

  lazy val shared = Seq(
    organization := "org.jupyter-scala",
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
    ),
    libs ++= {
      if (scalaBinaryVersion.value == "2.10")
        Seq(compilerPlugin(Deps.macroParadise))
      else
        Nil
    }
  ) ++ publishSettings

  lazy val testSettings = Seq(
    libs += Deps.utest % "test",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    licenses := Seq("LGPL-3.0" -> url("http://www.gnu.org/licenses/lgpl.txt")),
    scmInfo := Some(ScmInfo(
      url("https://github.com/alexarchambault/jupyter-kernel"),
      "git@github.com:alexarchambault/jupyter-kernel.git"
    )),
    homepage := Some(url("https://github.com/alexarchambault/jupyter-kernel")),
    pomExtra := {
      <developers>
        <developer>
          <id>alexarchambault</id>
          <name>Alexandre Archambault</name>
          <url>https://github.com/alexarchambault</url>
        </developer>
      </developers>
    },
    credentials ++= {
      Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
        case Seq(Some(user), Some(pass)) =>
          Seq(Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass))
        case _ =>
          Nil
      }
    },
    scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" =>
          Seq("-target:jvm-1.7")
        case _ =>
          Nil
      }
    }
  )

  lazy val dontPublish = Seq(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )

  lazy val kernelPrefix = {
    name := "kernel-" + name.value
  }

}
