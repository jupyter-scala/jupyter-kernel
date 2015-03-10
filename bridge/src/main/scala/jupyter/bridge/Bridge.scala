package jupyter.bridge

import scala.reflect.runtime.universe._
import scala.util.control.ControlThrowable

// Bridge API, similar to the one of Ammonite

trait BridgeHolder {
  var bridge0: Bridge = null
  lazy val bridge = bridge0
}

/**
 * Thrown to exit the REPL cleanly
 */
case object ReplExit extends ControlThrowable

trait Bridge {
  /**
   * Exit the Ammonite REPL. You can also use Ctrl-D to exit
   */
  def exit() = throw ReplExit

  /**
   * Clears the screen of the REPL
   */
  def clear(): Unit

  /**
   * History of commands that have been entered into the shell
   */
  def history: Seq[String]

  /**
   * Get the `Type` object of [[T]]. Useful for finding
   * what its methods are and what you can do with it
   */
  def typeOf[T: WeakTypeTag]: Type

  /**
   * Get the `Type` object representing the type of `t`. Useful
   * for finding what its methods are and what you can do with it
   *
   */
  def typeOf[T: WeakTypeTag](t: => T): Type

  /**
   * Tools related to loading external scripts and code into the REPL
   */
  def load: Load

  /**
   * Throw away the current scala.tools.nsc.Global and get a new one
   */
  def newCompiler(): Unit
}

trait Load extends (String => Unit) {
  /**
   * Load a `.jar` file
   */
  def jar(jar: java.io.File): Unit
  /**
   * Load a library from its maven/ivy coordinates
   */
  def ivy(coordinates: (String, String, String)): Unit
}

trait IvyConstructor {
  val scalaBinaryVersion = scala.util.Properties.versionNumberString
    .split('.').take(2).mkString(".")

  implicit class GroupIdExt(groupId: String) {
    def %(artifactId: String) = (groupId, artifactId)
    def %%(artifactId: String) = (groupId, s"${artifactId}_$scalaBinaryVersion")
  }
  implicit class ArtifactIdExt(t: (String, String)) {
    def %(version: String) = (t._1, t._2, version)
  }
}

object IvyConstructor extends IvyConstructor

object Bridge {
  def classLoader: ClassLoader = classOf[DisplayData].getClassLoader

  def init(holder: Class[BridgeHolder], bridge: Bridge): Unit = {
    val method = holder
      .getDeclaredMethods
      .find(_.getName == "bridge0_$eq")
      .get
    method.invoke(null, bridge)
  }
}
 