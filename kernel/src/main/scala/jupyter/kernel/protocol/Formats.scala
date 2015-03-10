package jupyter.kernel.protocol

import argonaut._, Argonaut._
import Output.ClearOutput
import shapeless.Witness

import scalaz.{\/-, -\/}

object Formats {
  implicit val decodeUUID = DecodeJson[NbUUID] { c =>
    StringDecodeJson.decode(c).flatMap { s =>
      NbUUID.fromString(s) match {
        case Some(uuid) =>
          DecodeResult.ok(uuid)
        case None =>
          DecodeResult.fail(s"Invalid UUID: $s", c.history)
      }
    }
  }
  
  implicit val encodeUUID = EncodeJson[NbUUID] { uuid =>
    Json.jString(uuid.toString)
  }

  implicit val encodeExecutionStatusOk: EncodeJson[ExecutionStatus.ok.type] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus.ok.type](_.toString)

  implicit val decodeExecutionStatusOk: DecodeJson[ExecutionStatus.ok.type] =
    DecodeJson[ExecutionStatus.ok.type] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "ok" => DecodeResult.ok(ExecutionStatus.ok)
        case _ => DecodeResult.fail("Expected ok status", c.history)
      }
    }

  implicit val encodeExecutionStatusError: EncodeJson[ExecutionStatus.error.type] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus.error.type](_.toString)

  implicit val decodeExecutionStatusError: DecodeJson[ExecutionStatus.error.type] =
    DecodeJson[ExecutionStatus.error.type] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "error" => DecodeResult.ok(ExecutionStatus.error)
        case _ => DecodeResult.fail("Expected error status", c.history)
      }
    }

  implicit val encodeExecutionStatusAbort: EncodeJson[ExecutionStatus.abort.type] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus.abort.type](_.toString)

  implicit val decodeExecutionStatusAbort: DecodeJson[ExecutionStatus.abort.type] =
    DecodeJson[ExecutionStatus.abort.type] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "abort" => DecodeResult.ok(ExecutionStatus.abort)
        case _ => DecodeResult.fail("Expected abort status", c.history)
      }
    }

  implicit val encodeExecutionStatus: EncodeJson[ExecutionStatus] =
    EncodeJson.StringEncodeJson.contramap[ExecutionStatus](_.toString)

  implicit val decodeExecutionStatus: DecodeJson[ExecutionStatus] =
    DecodeJson[ExecutionStatus] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "ok" => DecodeResult.ok(ExecutionStatus.ok)
        case "error" => DecodeResult.ok(ExecutionStatus.error)
        case "abort" => DecodeResult.ok(ExecutionStatus.abort)
        case _ => DecodeResult.fail("Expected execution status (ok|error|abort)", c.history)
      }
    }

  implicit val encodeHistAccessType: EncodeJson[HistAccessType] =
    EncodeJson.StringEncodeJson.contramap[HistAccessType](_.toString)

  implicit val decodeHistAccessType: DecodeJson[HistAccessType] =
    DecodeJson[HistAccessType] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "range" => DecodeResult.ok(HistAccessType.range)
        case "tail" => DecodeResult.ok(HistAccessType.tail)
        case "search" => DecodeResult.ok(HistAccessType.search)
        case _ => DecodeResult.fail("Expected hist access type (range|tail|search)", c.history)
      }
    }

  implicit val encodeExecutionState: EncodeJson[ExecutionState] =
    EncodeJson.StringEncodeJson.contramap[ExecutionState](_.toString)

  implicit val decodeExecutionState: DecodeJson[ExecutionState] =
    DecodeJson[ExecutionState] { c =>
      DecodeJson.StringDecodeJson.decode(c).flatMap {
        case "busy" => DecodeResult.ok(ExecutionState.busy)
        case "idle" => DecodeResult.ok(ExecutionState.idle)
        case "starting" => DecodeResult.ok(ExecutionState.starting)
        case _ => DecodeResult.fail("Expected execution state (busy|idle|starting)", c.history)
      }
    }

  implicit val encodeClearOutput: EncodeJson[ClearOutput] =
    EncodeJson[ClearOutput] { c =>
      Json("wait" -> Json.jBool(c._wait))
    }

  implicit val decodeClearOutput: DecodeJson[ClearOutput] =
    DecodeJson[ClearOutput] { c =>
      c.--\("").focus match {
        case Some(b) => DecodeJson.BooleanDecodeJson.decodeJson(b).map(ClearOutput)
        case None => DecodeResult.fail("ClearOutput", c.history)
      }
    }
  
  implicit def decodeEither[L: DecodeJson, R: DecodeJson]: DecodeJson[Either[L, R]] =
    DecodeJson[Either[L, R]] { c =>
      implicitly[DecodeJson[L]].decode(c).result match {
        case -\/(err) => implicitly[DecodeJson[R]].decode(c).map(Right(_))
        case \/-(l) => DecodeResult.ok(Left(l))
      }
    }

  implicit def encodeEither[L: EncodeJson, R: EncodeJson]: EncodeJson[Either[L, R]] =
    EncodeJson[Either[L, R]] {
      case Left(l) => implicitly[EncodeJson[L]].encode(l)
      case Right(r) => implicitly[EncodeJson[R]].encode(r)
    }

  // These are not used for now

  implicit val encodeFalse: EncodeJson[Witness.`false`.T] =
    EncodeJson { _ => Json.jBool(false) }

  implicit val decodeFalse: DecodeJson[Witness.`false`.T] =
    DecodeJson { c =>
      DecodeJson.BooleanDecodeJson.decode(c).flatMap {
        case false => DecodeResult.ok[Witness.`false`.T](false)
        case true => DecodeResult.fail("Expected false", c.history)
      }
    }

  implicit val encodeTrue: EncodeJson[Witness.`true`.T] =
    EncodeJson { _ => Json.jBool(true) }

  implicit val decodeTrue: DecodeJson[Witness.`true`.T] =
    DecodeJson { c =>
      DecodeJson.BooleanDecodeJson.decode(c).flatMap {
        case true => DecodeResult.ok[Witness.`true`.T](true)
        case false => DecodeResult.fail("Expected true", c.history)
      }
    }
}
