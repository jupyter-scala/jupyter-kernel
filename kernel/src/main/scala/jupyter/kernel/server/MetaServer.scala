package jupyter
package kernel
package server

import MessageSocket.Channel
import com.typesafe.scalalogging.slf4j.LazyLogging
import socket.zmq.{ZMQMessageSocket, ZMQKernel}

import argonaut._, Argonaut.{ EitherDecodeJson => _, EitherEncodeJson => _, _ }, Shapeless._
import protocol.{ Meta => MetaProtocol, _ }, Formats._

import scalaz.\/

object MetaServer extends LazyLogging {
  def handler(
    send: (Channel, Message) => Unit,
    launchKernel: ZMQMessageSocket => Unit,
    kernelId: String
  ): Message => Unit =
    _.decode.map { msg =>
      (msg.header.msg_type,  msg.content) match {
        case ("meta_kernel_start_request", startRequest: MetaProtocol.MetaKernelStartRequest) =>
          val c =
            for {
              connection <- \/.fromTryCatchNonFatal(ZMQKernel.newConnection())
              kernelSocket <- connection.start(isServer = false, identity = Some(kernelId))
              _ <- \/.fromTryCatchNonFatal(launchKernel(kernelSocket))
            } yield connection

          send(
            Channel.Requests,
            msg.reply(
              "meta_kernel_start_reply",
              MetaProtocol.MetaKernelStartReply(
                c.leftMap(_.getMessage).toEither
              )
            )
          )
        case _ =>
          throw new Exception(s"Unrecognized message: $msg")
      }
    }

  def start(
    socket: MessageSocket,
    launchKernel: ZMQMessageSocket => Unit,
    kernelId: String
  ) = {
    logger debug "Starting meta kernel event loop"

    val mainLoop = MessageSocket.process(socket, handler(socket.send, launchKernel, kernelId))

    mainLoop setName "MetaRequestsEventLoop"
    mainLoop.start()

    mainLoop
  }
}
