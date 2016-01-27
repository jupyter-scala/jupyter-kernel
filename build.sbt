import com.typesafe.sbt.pgp.PgpKeys

lazy val api = project
  .settings(commonSettings)
  .settings(
    name := "jupyter-api"
  )

lazy val core = project
  .dependsOn(api)
  .settings(commonSettings ++ testSettings: _*)
  .settings(
    name := "jupyter-kernel",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.2.1",
      "com.github.alexarchambault" %% "argonaut-shapeless_6.1" % "1.0.0-M1",
      "org.zeromq" % "jeromq" % "0.3.4",
      "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
      "org.scalaz.stream" %% "scalaz-stream" % "0.6a"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageSrc) := true
  )

lazy val cli = project
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(packAutoSettings ++ publishPackTxzArchive ++ publishPackZipArchive: _*)
  .settings(
    name := "jupyter-meta-kernel",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "case-app" % "0.2.2",
      "ch.qos.logback" % "logback-classic" % "1.0.13"
    )
  )

lazy val root = project.in(file("."))
  .settings(name := "jupyter-kernel-root")
  .settings(commonSettings: _*)
  .aggregate(api, core, cli)


lazy val commonSettings = Seq(
  organization := "com.github.alexarchambault.jupyter",
  scalaVersion := "2.11.7",
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
  ),
  libraryDependencies ++= {
    if (scalaVersion.value startsWith "2.10.")
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full))
    else
      Seq()
  }
) ++ publishSettings

lazy val testSettings = Seq(
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "utest" % "0.3.0" % "test"
  ),
  testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val publishSettings = com.atlassian.labs.gitstamp.GitStampPlugin.gitStampSettings ++ Seq(
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
  },
  scalacOptions += "-target:jvm-1.7",
  crossScalaVersions := Seq("2.10.5", "2.11.7"),
  ReleaseKeys.versionBump := sbtrelease.Version.Bump.Bugfix,
  ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
) ++ releaseSettings