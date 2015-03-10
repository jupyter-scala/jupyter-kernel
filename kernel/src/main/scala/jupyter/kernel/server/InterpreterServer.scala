package jupyter
package kernel
package server

import MessageSocket.Channel
import com.typesafe.scalalogging.slf4j.LazyLogging
import interpreter.{InterpreterHandler, Interpreter}
import protocol._, Formats._, Output.ConnectReply

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }, Shapeless._

object InterpreterServer extends LazyLogging {
  private def sendStatus(send: (Channel, Message) => Unit, parentHeader: Option[Header], state: ExecutionState): Unit =
    send(
      Channel.Publish,
      Message(ParsedMessage(
        "status" :: Nil,
        Header(msg_id=NbUUID.randomUUID(),
          username="scala_kernel",
          session=NbUUID.randomUUID(),
          msg_type="status",
          version = Protocol.versionStrOpt
        ),
        parentHeader,
        Map.empty,
        Output.Status(
          execution_state=state)
      ))
    )

  private def sendStarting(send: (Channel, Message) => Unit, parentHeader: Option[Header]) =
    sendStatus(send, parentHeader, ExecutionState.starting)


  // FIXME Move elsewhere
  def start(
    socket: MessageSocket,
    connectReply: ConnectReply,
    interpreter: Interpreter
  ) = {
    logger debug "Starting kernel event loop"
    sendStarting(socket.send, None)

    val mainLoop = MessageSocket.process(socket, InterpreterHandler(socket.send, connectReply, interpreter))

    mainLoop setName "RequestsEventLoop"
    mainLoop.start()

    mainLoop
  }
}
