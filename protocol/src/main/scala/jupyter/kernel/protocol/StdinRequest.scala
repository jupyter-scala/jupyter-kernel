package jupyter.kernel.protocol

sealed abstract class StdinRequest extends Product with Serializable

object StdinRequest {

  final case class Input(
    prompt: String,
    password: Boolean
  ) extends StdinRequest

}
