package jupyter.kernel.protocol

import argonaut.Json

sealed abstract class Publish extends Product with Serializable

object Publish {

  case class Stream(
    name: String,
    text: String
  ) extends Publish

  case class DisplayData(
    data: Map[String, Json], // keys are always string, except if key is "application/json"
    metadata: Map[String, Json]
  ) extends Publish

  case class ExecuteInput(
    code: String,
    execution_count: Int
  ) extends Publish

  case class ExecuteResult(
    execution_count: Int,
    data: Map[String, Json], // same as DisplayData
    metadata: Map[String, Json]
  ) extends Publish

  case class Error(
    ename: String,
    evalue: String,
    traceback: List[String]
  ) extends Publish

  // Spec says: Busy and idle messages should be sent before/after handling every message, not just execution.
  case class Status(
    execution_state: ExecutionState0
  ) extends Publish

  sealed abstract class ExecutionState0(val label: String) extends Product with Serializable

  object ExecutionState0 {
    case object Busy extends ExecutionState0("busy")
    case object Idle extends ExecutionState0("idle")
    case object Starting extends ExecutionState0("starting")
  }

  case class ClearOutput(
    wait0: Boolean // beware: just "wait" in the spec, but "wait" can't be used as an identifier here
  ) extends Publish

}
