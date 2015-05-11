package jupyter
package kernel
package server

import MessageSocket.Channel
import com.typesafe.scalalogging.slf4j.LazyLogging
import interpreter.{InterpreterHandler, Interpreter}
import protocol._, Formats._, Output.ConnectReply

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }

import acyclic.file

object InterpreterServer extends LazyLogging {
  def apply(
    socket: MessageSocket,
    connectReply: ConnectReply,
    interpreter: Interpreter
  ) = {
    logger debug "Starting kernel event loop"
    socket.send(
      Channel.Publish,
      ParsedMessage(
        "status" :: Nil,
        Header(
          msg_id = NbUUID.randomUUID(),
          username = "scala_kernel",
          session = NbUUID.randomUUID(),
          msg_type = "status",
          version = Protocol.versionStrOpt
        ),
        None,
        Map.empty,
        Output.Status(ExecutionState.starting)
      ).toMessage
    )

    val mainLoop = MessageSocket.process(socket, InterpreterHandler(interpreter, connectReply, _).map((socket.send _).tupled).run.run)

    mainLoop setName "RequestsEventLoop"
    mainLoop.start()

    mainLoop
  }
}
