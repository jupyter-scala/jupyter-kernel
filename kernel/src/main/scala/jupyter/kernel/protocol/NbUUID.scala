package jupyter.kernel.protocol

import java.util.UUID
import acyclic.file

// Adapted from IScala

class NbUUID private (uuid: UUID, dashes: Boolean = true, upper: Boolean = false) {
  override def toString: String = {
    val repr0 = uuid.toString
    val repr1 = if (dashes) repr0 else repr0.replace("-", "")
    
    if (upper) repr1.toUpperCase else repr1
  }
}

object NbUUID {
  def fromString(uuid: String): Option[NbUUID] = {
    val (actualUuid, dashes) =
      if (uuid contains "-") 
        (uuid, true)
      else 
        (List(
          uuid.slice( 0,  8),
          uuid.slice( 8, 12),
          uuid.slice(12, 16),
          uuid.slice(16, 20),
          uuid.slice(20, 32)
        ) mkString "-", false)

    try { Some(new NbUUID(UUID.fromString(actualUuid), dashes, upper = uuid.exists("ABCDF" contains _))) }
    catch { case _: java.lang.IllegalArgumentException => None }
  }

  def randomUUID(): NbUUID = new NbUUID(UUID.randomUUID())
}
