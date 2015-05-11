//package jupyter
//package kernel
//package socket
//package zmq
//
//import java.io.File
//
//import MessageSocket.Channel
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import protocol._, Formats._
//import argonaut._, Argonaut._
//import scalaz.{\/-, -\/, \/}
//
//import acyclic.file
//
//object ZMQMetaKernel {
//  def apply(metaConnectionFile: File, kernelId: String, keepAlive: Boolean): SocketKernel = new SocketKernel with LazyLogging {
//    val metaSocket: () => (Throwable \/ MessageSocket) = {
//      def helper(): Throwable \/ MessageSocket =
//        for {
//          lines <- \/.fromTryCatchNonFatal(scala.io.Source.fromFile(metaConnectionFile).mkString)
//          c <- lines.decodeEither[Connection].leftMap(s => new Exception(s"Error while reading ${metaConnectionFile.getAbsolutePath}: $s"))
//          _ <- \/.fromTryCatchNonFatal(preStart(metaConnectionFile))
//          socket <- ZMQMessageSocket.start(c, isServer = true, identity = Some(kernelId))
//        } yield socket
//
//      if (keepAlive) {
//        // FIXME If using a lazy val for s, running into a weird scalac error:
//        //   java.lang.AssertionError: assertion failed: inconvertible types : BYTE -> BOOL
//        var s: Throwable \/ MessageSocket = null
//        () => synchronized {
//          if (s == null)
//            s = helper()
//          s
//        }
//      } else
//        () => helper()
//    }
//
//    def preStart(connectionFile: File): Unit = {}
//
//    def socket(classLoader: Option[ClassLoader]) =
//      metaSocket() flatMap { meta =>
//        try {
//          for {
//            _ <- \/.fromTryCatchNonFatal {
//              meta.send(Channel.Requests, ParsedMessage(
//                Nil,
//                Header(NbUUID.randomUUID(), "", NbUUID.randomUUID() /* FIXME*/, "meta_kernel_start_request", Protocol.versionStrOpt),
//                None,
//                Map.empty,
//                Meta.MetaKernelStartRequest()
//              ).toMessage)
//            }
//            rawReply <- meta.receive(Channel.Requests).leftMap(s => new Exception(s"Receiving message: $s"))
//            msg <- rawReply.decode.flatMap { msg =>
//              msg.content match {
//                case r: Meta.MetaKernelStartReply => \/-(r)
//                case _ => -\/(s"Unrecognized message: $msg")
//              }
//            } .leftMap(s => new Exception(s"Decoding message (meta): $s"))
//            connection <- \/.fromEither(msg.connection) .leftMap(s => new Exception(s"From kernel (meta): $s"))
//            socket <- ZMQMessageSocket.start(connection, isServer = true, identity = Some(kernelId))
//          } yield socket
//        } finally {
//          if (!keepAlive) meta.close()
//        }
//      }
//  }
//}
//
