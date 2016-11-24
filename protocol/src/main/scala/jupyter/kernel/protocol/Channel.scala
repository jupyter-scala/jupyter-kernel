package jupyter.kernel.protocol

sealed abstract class Channel extends Product with Serializable

object Channel {
  case object  Requests extends Channel
  case object   Control extends Channel
  case object   Publish extends Channel
  case object     Input extends Channel

  val channels = Seq(Requests, Control, Publish, Input)
}
