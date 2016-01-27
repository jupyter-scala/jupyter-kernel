package jupyter.kernel.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Adapted from the implementation of IScala

sealed trait HMAC {
  def apply(args: String*): String
}

object HMAC {

  private val empty = new HMAC {
    def apply(args: String*) = ""
  }

  def apply(key: String, algorithm: Option[String] = None): HMAC =
    if (key.isEmpty)
      empty
    else
      new HMAC {
        private val algorithm0 = "hmac-sha256".replace("-", "")
        private val mac = Mac.getInstance(algorithm0)
        private val keySpec = new SecretKeySpec(key.getBytes("UTF-8"), algorithm0)

        mac.init(keySpec)

        private def hex(bytes: Seq[Byte]) = bytes.map(s => f"$s%02x").mkString

        def apply(args: String*) =
          mac.synchronized {
            for (s <- args)
              mac.update(s.getBytes("UTF-8"))

            hex(mac.doFinal())
          }
      }
}
