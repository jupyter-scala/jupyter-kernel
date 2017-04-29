
import Aliases._
import Settings._

lazy val api = project
  .settings(
    shared,
    kernelPrefix
  )

lazy val protocol = project
  .settings(
    shared,
    kernelPrefix,
    libs += Deps.argonaut
  )

lazy val core = project
  .dependsOn(api, protocol)
  .settings(
    shared,
    name := "kernel",
    testSettings,
    libs ++= Seq(
      Deps.argonautShapeless,
      Deps.jeromq,
      Deps.scalazStream
    ),
    libs += Deps.scalaLogging.value,
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageSrc) := true
  )

lazy val `jupyter-kernel` = project
  .in(root)
  .aggregate(
    api,
    protocol,
    core
  )
  .settings(
    shared,
    dontPublish
  )

