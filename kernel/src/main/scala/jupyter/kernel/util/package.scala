package jupyter.kernel

import java.util.concurrent.LinkedBlockingQueue

import scalaz.concurrent.Task
import scalaz.stream.Process

package object util {

  def blockingQueueStream[T](): (LinkedBlockingQueue[Option[T]], Process[Task, T]) = {
    val q = new LinkedBlockingQueue[Option[T]]

    def p(): Process[Task, T] =
      Process.await(Task(q.take())) {
        case Some(t) =>
          Process.emit(t) ++ p()
        case None =>
          Process.halt
      }

    (q, p())
  }

}
