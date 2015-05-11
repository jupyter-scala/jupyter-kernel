package jupyter
package kernel
package interpreter

import scalaz.\/
import acyclic.file

trait InterpreterKernel extends Kernel {
  def interpreter(classLoader: Option[ClassLoader]): Throwable \/ Interpreter
}
