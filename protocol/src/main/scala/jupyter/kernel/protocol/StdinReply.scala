package jupyter.kernel.protocol

sealed abstract class StdinReply extends Product with Serializable

object StdinReply {

  final case class Input(
    value: String
  ) extends StdinReply

}
