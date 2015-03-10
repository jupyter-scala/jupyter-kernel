package jupyter
package kernel
package interpreter

import scalaz.\/

trait InterpreterKernel extends Kernel {
  def interpreter(classLoader: Option[ClassLoader]): Throwable \/ Interpreter
}
