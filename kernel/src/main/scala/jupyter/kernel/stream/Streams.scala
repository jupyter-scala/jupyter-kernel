package jupyter.kernel
package stream

import com.typesafe.scalalogging.slf4j.LazyLogging

import scalaz.{-\/, \/-, \/}
import scalaz.concurrent.Task
import scalaz.stream.{Sink, Process}

case class Streams(
  requestMessages: Process[Task, String \/ Message],
  requestSink: Sink[Task, Message],
  controlMessages: Process[Task, String \/ Message],
  controlSink: Sink[Task, Message],
  publishMessages: Process[Task, String \/ Message],
  publishSink: Sink[Task, Message],
  inputMessages: Process[Task, String \/ Message],
  inputSink: Sink[Task, Message],
  stop: () => Unit
)

object Streams extends LazyLogging {
  def connect(a: Streams, b: Streams): Task[Unit] = {
    def collect(channelName: String): String \/ Message => Process[Task, Message] = {
      case -\/(err) =>
        logger warn s"Failed to decode message on $channelName: $err"
        Process.empty
      case \/-(m) =>
        Process.emit(m)
    }
    
    Task.gatherUnordered(Seq(
      a.requestMessages.flatMap(collect("request(>)")).to(b.requestSink).run,
      b.requestMessages.flatMap(collect("request(<)")).to(a.requestSink).run,
      a.controlMessages.flatMap(collect("control(>)")).to(b.controlSink).run,
      b.controlMessages.flatMap(collect("control(<)")).to(a.controlSink).run,
      a.publishMessages.flatMap(collect("publish(>)")).to(b.publishSink).run,
      b.publishMessages.flatMap(collect("publish(<)")).to(a.publishSink).run,
      a.inputMessages.flatMap(collect("input(>)")).to(b.inputSink).run,
      b.inputMessages.flatMap(collect("input(<)")).to(a.inputSink).run
    )).map(_ => ())
  }
}
