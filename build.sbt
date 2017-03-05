
val argonautVersion = "6.2-RC2"
val argonautShapelessVersion = "1.2.0-M4"
val fs2Version = "0.8.6a"
val jeromqVersion = "0.3.6"
val scalaLoggingVersion = "3.5.0"
val utestVersion = "0.4.4"

lazy val `kernel-api` = project.in(file("api"))
  .settings(commonSettings)

lazy val `kernel-protocol` = project.in(file("protocol"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.argonaut" %% "argonaut" % argonautVersion
    )
  )

lazy val kernel = project.in(file("core"))
  .dependsOn(`kernel-api`, `kernel-protocol`)
  .settings(commonSettings)
  .settings(testSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % argonautShapelessVersion,
      "org.zeromq" % "jeromq" % jeromqVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      "org.scalaz.stream" %% "scalaz-stream" % fs2Version
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageSrc) := true
  )

lazy val `jupyter-kernel` = project.in(file("."))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .aggregate(`kernel-api`, `kernel-protocol`, kernel)


lazy val commonSettings = Seq(
  organization := "org.jupyter-scala",
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
  ),
  libraryDependencies ++= {
    if (scalaBinaryVersion.value == "2.10") Seq(
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ) else Nil
  }
) ++ publishSettings

lazy val testSettings = Seq(
  libraryDependencies += "com.lihaoyi" %% "utest" % utestVersion % "test",
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
  credentials += {
    Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
      case Seq(Some(user), Some(pass)) =>
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
      case _ =>
        Credentials(Path.userHome / ".ivy2" / ".credentials")
    }
  },
  scalacOptions += "-target:jvm-1.7"
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)
