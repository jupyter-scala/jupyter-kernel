package jupyter.kernel

import java.util.concurrent.LinkedBlockingQueue

import scalaz.concurrent.Task
import scalaz.stream.Process

package object stream {

  def toLinkedQueue[T](p: Process[Task, T]): LinkedBlockingQueue[T] = {
    val q = new LinkedBlockingQueue[T]
    p.map(q.put).run.run
    q
  }

}
