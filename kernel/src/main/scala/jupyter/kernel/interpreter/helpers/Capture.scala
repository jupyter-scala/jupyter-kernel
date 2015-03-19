package jupyter.kernel.interpreter.helpers

import acyclic.file

// From IScala

object Capture {
  private def watchStream(input: java.io.InputStream, size: Int, fn: String => Unit): Thread = new Thread {
    override def run() = {
      val buffer = Array.ofDim[Byte](size)

      try {
        while (true) {
          val n = input read buffer
          fn(new String(buffer take n))

          if (n < size)
            Thread.sleep(50) // a little delay to accumulate output
        }
      } catch {
        case _: java.io.IOException => // stream was closed so job is done
      }
    }
  }

  private def withOut[T](newOut: java.io.PrintStream)(block: => T): T = {
    Console.withOut(newOut) {
      val oldOut = System.out
      System.setOut(newOut)
      try {
        block
      } finally {
        System.setOut(oldOut)
      }
    }
  }

  private def withErr[T](newErr: java.io.PrintStream)(block: => T): T = 
    Console.withErr(newErr) {
      val oldErr = System.err
      System.setErr(newErr)
      try block finally System.setErr(oldErr)
    }

  private def withOutAndErr[T](newOut: java.io.PrintStream,
                               newErr: java.io.PrintStream)(block: => T): T = 
    withOut(newOut) {
      withErr(newErr) {
        block
      }
    }

  // This is a heavyweight solution to start stream watch threads per
  // input, but currently it's the cheapest approach that works well in
  // multiple thread setup. Note that piped streams work only in thread
  // pairs (producer -> consumer) and we start one thread per execution,
  // so technically speaking we have multiple producers, which completely
  // breaks the earlier intuitive approach.

  def apply[T](stdout: String => Unit, stderr: String => Unit)(block: => T): T = {
    val size = 10240

    val stdoutIn = new java.io.PipedInputStream(size)
    val stderrIn = new java.io.PipedInputStream(size)

    val stderrOut = new java.io.PipedOutputStream(stderrIn)
    val stdoutOut = new java.io.PipedOutputStream(stdoutIn)

    val _stdout = new java.io.PrintStream(stdoutOut)
    val _stderr = new java.io.PrintStream(stderrOut)

    val stdoutThread = watchStream(stdoutIn, size, stdout)
    val stderrThread = watchStream(stderrIn, size, stderr)

    stdoutThread.start()
    stderrThread.start()

    try {
      val result = withOutAndErr(_stdout, _stderr) { block }

      stdoutOut.flush()
      stderrOut.flush()

      // Wait until both streams get dry because we have to
      // send messages with streams' data before execute_reply
      // is send. Otherwise there will be no output in clients
      // or it will be incomplete.
      while (stdoutIn.available > 0 || stderrIn.available > 0)
        Thread.sleep(10)

      result
    } finally {
      // This will effectively terminate threads.
      stdoutOut.close()
      stderrOut.close()
      stdoutIn.close()
      stderrIn.close()
    }
  }
}