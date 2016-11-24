package jupyter.kernel.protocol

sealed abstract class StdinReply extends Product with Serializable

object StdinReply {

  case class Input(
    value: String
  ) extends StdinReply

}
