package jupyter.kernel.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Adapted from the implementation of IScala

sealed abstract class HMAC {
  def apply(args: String*): String
}

object HMAC {

  private def instance(f: Seq[String] => String): HMAC =
    new HMAC {
      def apply(args: String*) = f(args)
    }

  private val empty = instance(_ => "")

  def apply(key: String, algorithm: Option[String] = None): HMAC =
    if (key.isEmpty)
      empty
    else {
      val algorithm0 = "hmac-sha256".replace("-", "")
      val mac = Mac.getInstance(algorithm0)
      val keySpec = new SecretKeySpec(key.getBytes("UTF-8"), algorithm0)

      mac.init(keySpec)

      def hex(bytes: Seq[Byte]) = bytes.map(s => f"$s%02x").mkString

      instance { args =>
        mac.synchronized {
          for (s <- args)
            mac.update(s.getBytes("UTF-8"))

          hex(mac.doFinal())
        }
      }
    }
}
