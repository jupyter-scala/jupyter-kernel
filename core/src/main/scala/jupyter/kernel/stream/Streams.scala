package jupyter.kernel
package stream

import jupyter.kernel.protocol.Channel

import scalaz.concurrent.Task
import scalaz.stream.{ Process, Sink }

final case class Streams(
  processes: Channel => (Process[Task, Either[String, Message]], Sink[Task, Message]),
  stop: () => Unit
)
