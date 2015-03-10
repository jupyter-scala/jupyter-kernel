import language.implicitConversions
import sbt._, Keys._
import xerial.sbt.Pack._
import sbtbuildinfo.Plugin._
import sbtrelease.ReleasePlugin._
import com.typesafe.sbt.pgp.PgpKeys

object JupyterBuild extends Build {
  private val publishSettings = xerial.sbt.Sonatype.sonatypeSettings ++ com.atlassian.labs.gitstamp.GitStampPlugin.gitStampSettings ++ Seq(
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    licenses := Seq("LGPL-3.0" -> url("http://www.gnu.org/licenses/lgpl.txt")),
    scmInfo := Some(ScmInfo(url("https://github.com/alexarchambault/jupyter-kernel"), "git@github.com:alexarchambault/jupyter-kernel.git")),
    pomExtra := {
      <url>https://github.com/alexarchambault/jupyter-kernel</url>
      <developers>
        <developer>
          <id>alexarchambault</id>
          <name>Alexandre Archambault</name>
          <url>https://github.com/alexarchambault</url>
        </developer>
      </developers>
    },
    credentials += {
      Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
        case Seq(Some(user), Some(pass)) =>
          Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
        case _ =>
          Credentials(Path.userHome / ".ivy2" / ".credentials")
      }
    }
  )

  private val commonSettings = Seq(
    organization := "com.github.alexarchambault.jupyter",
    scalaVersion := "2.11.6",
    crossScalaVersions := Seq("2.10.5", "2.11.6"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    resolvers ++= Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots")
    ),
    scalacOptions += "-target:jvm-1.7",
    ReleaseKeys.versionBump := sbtrelease.Version.Bump.Bugfix,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
  ) ++ releaseSettings ++ packSettings ++ publishSettings

  lazy val bridge = Project(id = "bridge", base = file("bridge"))
    .settings(commonSettings: _*)
    .settings(buildInfoSettings: _*)
    .settings(
      name := "jupyter-bridge",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      unmanagedSourceDirectories in Compile += (sourceDirectory in Compile).value / s"scala-${scalaBinaryVersion.value}"
    )

  lazy val kernel = Project(id = "kernel", base = file("kernel"))
    .settings(commonSettings: _*)
    .settings(buildInfoSettings: _*)
    .settings(
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        sbtVersion
      ),
      buildInfoPackage := "jupyter.kernel"
    )
    .settings(
      name := "jupyter-kernel",
      libraryDependencies ++= Seq(
        "com.typesafe" % "config" % "1.2.1",
        "commons-codec" % "commons-codec" % "1.9",
        "com.github.alexarchambault" %% "argonaut-shapeless_6.1" % "0.1.0",
        "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
        "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
        "org.zeromq" % "jeromq" % "0.3.4",
        "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"
      ),
      libraryDependencies ++= {
        if (scalaVersion.value startsWith "2.10.") Seq(
          "com.chuusai" % "shapeless_2.10.4" % "2.1.0-RC2", // FIXME Switch to _2.10.5 once it is published
          compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
        ) else Seq(
          "com.chuusai" %% "shapeless" % "2.1.0-RC2"
        )
      },
      unmanagedSourceDirectories in Compile +=
        (sourceDirectory in Compile).value / s"scala-${scalaBinaryVersion.value}"
    )
    .dependsOn(bridge)

  lazy val metaKernel = Project(id = "meta-kernel", base = file("meta-kernel"))
    .settings(commonSettings: _*)
    .settings(conscript.Harness.conscriptSettings: _*)
    .settings(packSettings ++ publishPackArchive: _*)
    .settings(
      name := "jupyter-meta-kernel",
      libraryDependencies ++= Seq(
        "com.github.alexarchambault" %% "case-app" % "0.2.1",
        "ch.qos.logback" % "logback-classic" % "1.0.13"
      ),
      libraryDependencies ++= {
        if (scalaVersion.value startsWith "2.10.")
          Seq(
            compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
          )
        else
          Seq()
      },
      // Should not be necessary with the next release of sbt-pack (> 0.6.5) and packAutoSettings
      packMain := Map(
        "jupyter-meta-kernel" -> "jupyter.kernel.meta.JupyterMetaKernel"
      )
    )
    .dependsOn(kernel)

  lazy val root = Project(id = "jupyter-kernel", base = file("."))
    .settings(commonSettings: _*)
    .aggregate(bridge, kernel, metaKernel)
}
