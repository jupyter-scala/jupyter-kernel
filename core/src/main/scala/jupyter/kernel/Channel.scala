package jupyter
package kernel

import acyclic.file

sealed trait Channel

object Channel {
  case object   Publish extends Channel
  case object  Requests extends Channel
  case object   Control extends Channel
  case object     Input extends Channel

  val channels = Seq(Requests, Publish, Control, Input)
}