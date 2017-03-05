package jupyter.kernel.protocol

import argonaut.Json

sealed abstract class Comm extends Product with Serializable {
  def comm_id: String
  def data: Json
}

object Comm {

  // Spec says: If the target_name key is not found on the receiving side, then it should immediately reply with a comm_close message to avoid an inconsistent state.
  final case class Open(
    comm_id: String,
    target_name: String,
    data: Json,
    target_module: Option[String] = None // spec says: used to select a module that is responsible for handling the target_name.
  ) extends Comm

  final case class Message(
    comm_id: String,
    data: Json
  ) extends Comm

  final case class Close(
    comm_id: String,
    data: Json
  ) extends Comm

}
