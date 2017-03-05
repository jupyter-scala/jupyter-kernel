package jupyter.kernel.protocol

sealed abstract class ShellRequest extends Product with Serializable

object ShellRequest {

  final case class Execute(
    code: String,
    user_expressions: Map[String, String],
    silent: Option[Boolean] = None,
    store_history: Option[Boolean] = None,
    allow_stdin: Option[Boolean] = None,
    stop_on_error: Option[Boolean] = None
  ) extends ShellRequest

  final case class Inspect(
    code: String,
    cursor_pos: Int,
    detail_level: Int // 0 or 1 - enforce with refined, or a custom ADT?
  ) extends ShellRequest

  final case class Complete(
    code: String,
    cursor_pos: Int
  ) extends ShellRequest

  sealed abstract class History extends Product with Serializable {
    def output: Boolean
    def raw: Boolean
    def hist_access_type: History.AccessType
  }

  object History {

    sealed abstract class AccessType extends Product with Serializable
    object AccessType {
      case object Range extends AccessType
      case object Tail extends AccessType
      case object Search extends AccessType

      // required for the type class derivation to be fine in 2.10
      type Range = Range.type
      type Tail = Tail.type
      type Search = Search.type
    }

    final case class Range(
      output: Boolean,
      raw: Boolean,
      session: Int, // range specific
      start: Int, // range specific
      stop: Int, // range specific
      hist_access_type: AccessType.Range
    ) extends History

    object Range {
      def apply(
        output: Boolean,
        raw: Boolean,
        session: Int, // range specific
        start: Int, // range specific
        stop: Int
      ): Range =
        Range(
          output,
          raw,
          session,
          start,
          stop,
          AccessType.Range
        )
    }

    final case class Tail(
      output: Boolean,
      raw: Boolean,
      n: Int, // tail and search specific
      hist_access_type: AccessType.Tail
    ) extends History

    object Tail {
      def apply(
        output: Boolean,
        raw: Boolean,
        n: Int
      ): Tail =
        Tail(
          output,
          raw,
          n,
          AccessType.Tail
        )
    }

    final case class Search(
      output: Boolean,
      raw: Boolean,
      n: Int, // search specific
      pattern: String, // search specific
      unique: Option[Boolean] = None, // search specific
      hist_access_type: AccessType.Search
    )

    object Search {
      def apply(
        output: Boolean,
        raw: Boolean,
        n: Int,
        pattern: String,
        unique: Option[Boolean]
      ): Search =
        Search(
          output,
          raw,
          n,
          pattern,
          unique,
          AccessType.Search
        )

      def apply(
        output: Boolean,
        raw: Boolean,
        n: Int,
        pattern: String
      ): Search =
        Search(
          output,
          raw,
          n,
          pattern,
          None
        )
    }

  }

  final case class IsComplete(
    code: String
  ) extends ShellRequest

  case object Connect extends ShellRequest

  final case class CommInfo(
    target_name: Option[String] = None
  ) extends ShellRequest

  case object KernelInfo extends ShellRequest

  final case class Shutdown(
    restart: Boolean
  ) extends ShellRequest

}
