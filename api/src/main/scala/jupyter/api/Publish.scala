package jupyter.api

import java.util.UUID

trait Publish extends Display {
  def stdout(text: String): Unit
  def stderr(text: String): Unit

  /** Opens a communication channel server -> client */
  def comm(id: String = UUID.randomUUID().toString): Comm

  /** Registers a client -> server message handler */
  def commHandler(target: String)(handler: CommChannelMessage => Unit): Unit
}
