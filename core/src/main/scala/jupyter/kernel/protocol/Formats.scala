package jupyter.kernel.protocol

import argonaut._, Argonaut._, ArgonautShapeless._
import argonaut.derive.{ JsonSumCodec, JsonSumCodecFor }

import shapeless.Typeable

object FormatHelpers {

  // TODO Move these to argonaut-shapeless?

  def jsonSumDirectCodecFor(name: String): JsonSumCodec = new JsonSumCodec {
    def encodeEmpty: Nothing =
      throw new IllegalArgumentException(s"empty $name")
    def encodeField(fieldOrObj: Either[Json, (String, Json)]): Json =
      fieldOrObj match {
        case Left(other) => other
        case Right((_, content)) => content
      }

    def decodeEmpty(cursor: HCursor): DecodeResult[Nothing] =
    // FIXME Sometimes reports the wrong error (in case of two nested sum types)
      DecodeResult.fail(s"unrecognized $name", cursor.history)
    def decodeField[A](name: String, cursor: HCursor, decode: DecodeJson[A]): DecodeResult[Either[ACursor, A]] =
      DecodeResult.ok {
        val o = decode
          .decode(cursor)
        o.toOption
          .toRight(ACursor.ok(cursor))
      }
  }

  def constantStringSingletonDecode[T](label: String, value: T): DecodeJson[T] =
    DecodeJson { c =>
      c.as[String].flatMap { s =>
        if (s == value)
          DecodeResult.ok(value)
        else
          DecodeResult.fail(s"Expected $label, got $s", c.history)
      }
    }

  def constantStringSingletonEncode[T](label: String): EncodeJson[T] = {
    val json = label.asJson
    EncodeJson(_ => json)
  }

  trait IsEnum[-T] {
    def label(t: T): String
  }

  object IsEnum {
    def apply[T](implicit isEnum: IsEnum[T]): IsEnum[T] = isEnum

    def instance[T](f: T => String): IsEnum[T] =
      new IsEnum[T] {
        def label(t: T) = f(t)
      }
  }

  implicit def isEnumEncoder[T: IsEnum]: EncodeJson[T] =
    EncodeJson.of[String].contramap(IsEnum[T].label)
  implicit def isEnumDecoder[T]
   (implicit
    isEnum: IsEnum[T],
    enum: Enumerate[T],
    typeable: Typeable[T]
   ): DecodeJson[T] =
    DecodeJson {
      val underlying = DecodeJson.of[String]
      val map = enum().map(e => isEnum.label(e) -> e).toMap
      val name = typeable.describe // TODO split in words

      c =>
        underlying(c).flatMap { s =>
          map.get(s) match {
            case None => DecodeResult.fail(s"Unrecognized $name: '$s'", c.history)
            case Some(m) => DecodeResult.ok(m)
          }
        }
    }

}

trait ShellRequestDecodeJsons {

  import ShellRequest._
  import FormatHelpers._

  private implicit val shellRequestHistoryJsonCodec =
    JsonSumCodecFor[History](jsonSumDirectCodecFor("history request"))

  private implicit val decodeShellRequestHistoryAccessTypeRange =
    constantStringSingletonDecode("range", History.AccessType.Range)
  private implicit val decodeShellRequestHistoryAccessTypeTail =
    constantStringSingletonDecode("tail", History.AccessType.Tail)
  private implicit val decodeShellRequestHistoryAccessTypeSearch =
    constantStringSingletonDecode("search", History.AccessType.Search)

  implicit val decodeShellRequestExecute = DecodeJson.of[Execute]
  implicit val decodeShellRequestInspect = DecodeJson.of[Inspect]
  implicit val decodeShellRequestComplete = DecodeJson.of[Complete]
  implicit val decodeShellRequestHistory = DecodeJson.of[History]
  implicit val decodeShellRequestIsComplete = DecodeJson.of[IsComplete]
  implicit val decodeShellRequestConnect = DecodeJson.of[Connect.type]
  implicit val decodeShellRequestCommInfo = DecodeJson.of[CommInfo]
  implicit val decodeShellRequestKernelInfo = DecodeJson.of[KernelInfo.type]
  implicit val decodeShellRequestShutdown = DecodeJson.of[Shutdown]

}

trait ShellRequestEncodeJsons {

  import ShellRequest._
  import FormatHelpers._

  private implicit val shellRequestHistoryJsonCodec =
    JsonSumCodecFor[History](jsonSumDirectCodecFor("history request"))

  private implicit val encodeShellRequestHistoryAccessTypeRange =
    constantStringSingletonEncode[History.AccessType.Range]("range")
  private implicit val encodeShellRequestHistoryAccessTypeTail =
    constantStringSingletonEncode[History.AccessType.Tail]("tail")
  private implicit val encodeShellRequestHistoryAccessTypeSearch =
    constantStringSingletonEncode[History.AccessType.Search]("search")

  implicit val encodeShellRequestExecute = EncodeJson.of[Execute]
  implicit val encodeShellRequestInspect = EncodeJson.of[Inspect]
  implicit val encodeShellRequestComplete = EncodeJson.of[Complete]
  implicit val encodeShellRequestHistory = EncodeJson.of[History]
  implicit val encodeShellRequestIsComplete = EncodeJson.of[IsComplete]
  implicit val encodeShellRequestConnect = EncodeJson.of[Connect.type]
  implicit val encodeShellRequestCommInfo = EncodeJson.of[CommInfo]
  implicit val encodeShellRequestKernelInfo = EncodeJson.of[KernelInfo.type]
  implicit val encodeShellRequestShutdown = EncodeJson.of[Shutdown]

}

trait ShellReplyEncodeJsons {

  import ShellReply._
  import FormatHelpers._

  private implicit val encodeShellReplyStatusOk =
    constantStringSingletonEncode[Status.Ok]("ok")
  private implicit val encodeShellReplyStatusAbort =
    constantStringSingletonEncode[Status.Abort]("abort")
  private implicit val encodeShellReplyStatusError =
    constantStringSingletonEncode[Status.Error]("error")

  private implicit val shellReplyHistoryJsonCodec =
    JsonSumCodecFor[History](jsonSumDirectCodecFor("history reply"))

  implicit val encodeShellReplyIsComplete: EncodeJson[IsComplete] = {

    case class Resp(status: String, indent: String = "")

    EncodeJson { isComplete =>

      val resp0 = Resp(isComplete.status)

      val resp = isComplete match {
        case ic @ IsComplete.Incomplete(indent) =>
          resp0.copy(indent = indent)
        case _ =>
          resp0
      }

      resp.asJson
    }
  }

  implicit val encodeShellReplyError = EncodeJson.of[Error]
  implicit val encodeShellReplyAbort = EncodeJson.of[Abort]
  implicit val encodeShellReplyExecute = EncodeJson.of[Execute]
  implicit val encodeShellReplyInspect = EncodeJson.of[Inspect]
  implicit val encodeShellReplyComplete = EncodeJson.of[Complete]
  implicit val encodeShellReplyHistory = EncodeJson.of[History]
  implicit val encodeShellReplyHistoryDefault = EncodeJson.of[History.Default]
  implicit val encodeShellReplyHistoryWithOutput = EncodeJson.of[History.WithOutput]
  implicit val encodeShellReplyConnect = EncodeJson.of[Connect]
  implicit val encodeShellReplyCommInfo = EncodeJson.of[CommInfo]
  implicit val encodeShellReplyKernelInfo = EncodeJson.of[KernelInfo]
  implicit val encodeShellReplyShutdown = EncodeJson.of[Shutdown]

}

trait PublishJsonCodecs {

  import Publish._
  import FormatHelpers._

  private implicit val publishExecutionStateIsEnum = IsEnum.instance[ExecutionState0](_.label)

  // add back support for records in argonaut-shapeless!!!
  implicit val encodePublishClearOutput: EncodeJson[ClearOutput] =
    EncodeJson {
      msg =>
        // Record(wait = msg.wait0).asJson
        Json.obj(
          "wait" -> msg.wait0.asJson
        )
    }
  implicit val decodePublishClearOutput: DecodeJson[ClearOutput] =
    DecodeJson { c =>
      // c.as[Record.`'wait -> Boolean`.T].map { rec =>
      //   ClearOutput(rec.wait)
      // }
      c.--\("wait").as[Boolean].map { w =>
        ClearOutput(w)
      }
    }

  implicit val encodePublishStream = EncodeJson.of[Stream]
  implicit val decodePublishStream = DecodeJson.of[Stream]
  implicit val encodePublishDisplayData = EncodeJson.of[DisplayData]
  implicit val decodePublishDisplayData = DecodeJson.of[DisplayData]
  implicit val encodePublishExecuteInput = EncodeJson.of[ExecuteInput]
  implicit val decodePublishExecuteInput = DecodeJson.of[ExecuteInput]
  implicit val encodePublishExecuteResult = EncodeJson.of[ExecuteResult]
  implicit val decodePublishExecuteResult = DecodeJson.of[ExecuteResult]
  implicit val encodePublishError = EncodeJson.of[Error]
  implicit val decodePublishError = DecodeJson.of[Error]
  implicit val encodePublishStatus = EncodeJson.of[Status]
  implicit val decodePublishStatus = DecodeJson.of[Status]

}

trait StdinRequestDecodeJsons {

  import StdinRequest._

  implicit val decodeStdinRequestInput = DecodeJson.of[Input]

}

trait StdinReplyEncodeJsons {

  import StdinReply._

  implicit val encodeStdinReplyInput = EncodeJson.of[Input]

}

trait CommJsonCodecs {

  import Comm._

  implicit val encodeCommOpen = EncodeJson.of[Open]
  implicit val decodeCommOpen = DecodeJson.of[Open]
  implicit val encodeCommMessage = EncodeJson.of[Message]
  implicit val decodeCommMessage = DecodeJson.of[Message]
  implicit val encodeCommClose = EncodeJson.of[Close]
  implicit val decodeCommClose = DecodeJson.of[Close]

}

trait HeaderJsonCodecs {

  implicit lazy val decodeHeader = DecodeJson.of[Header]
  implicit lazy val encodeHeader = EncodeJson.of[Header]

}

trait ConnectionJsonCodecs {

  implicit lazy val decodeConnection = DecodeJson.of[Connection]
  implicit lazy val encodeConnection = EncodeJson.of[Connection]
}

object Formats
  extends ShellRequestDecodeJsons
  with ShellRequestEncodeJsons
  with ShellReplyEncodeJsons
  with PublishJsonCodecs
  with StdinRequestDecodeJsons
  with StdinReplyEncodeJsons
  with CommJsonCodecs
  with HeaderJsonCodecs
  with ConnectionJsonCodecs