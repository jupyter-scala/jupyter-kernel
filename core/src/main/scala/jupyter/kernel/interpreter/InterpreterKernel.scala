package jupyter.kernel.interpreter

trait InterpreterKernel {
  def apply(): Interpreter
}
