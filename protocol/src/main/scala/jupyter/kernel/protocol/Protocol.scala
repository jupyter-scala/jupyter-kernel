package jupyter.kernel.protocol

object Protocol {
  val versionMajor = 5
  val versionMinor = 0

  val versionStrOpt: Option[String] = Some(s"$versionMajor.$versionMinor")
}
