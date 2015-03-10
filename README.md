# Jupyter kernel

*Helper library to write [Jupyter / IPython](http://ipython.org/) kernels on the JVM*

`jupyter-kernel` is a library that helps writing kernels for
[Jupyter / IPython](http://ipython.org/) on the JVM.
It mainly targets Scala for now, but could be used for other
languages as well in the future.


## Quick start

Add to your `build.sbt`,
```scala
resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies +=
  "com.github.alexarchambault.jupyter" %% "jupyter-kernel" % "0.2.0-SNAPSHOT"
```

Implementing a kernel is then a matter of providing an implementation
of the `jupyter.interpreter.Interpreter` trait,
```scala
trait Interpreter {
  def interpret(line: String, output: Option[(String => Unit, String => Unit)], storeHistory: Boolean): Result
  def complete(s: String): List[String] // Subject to change
  def executionCount: Int
  def reset(): Unit
  def stop(): Unit
}
```
and creating an instance of a `jupyter.interpreter.InterpreterKernel`
that instantiates the `Interpreter`,
```scala
trait InterpreterKernel extends Kernel {
  def interpreter(classLoader: Option[ClassLoader]): Throwable \/ Interpreter
}
```

Then one has just to call the method `apply` of
`jupyter.kernel.server.ServerApp` to wrap this kernel in a CLI
program,
```scala
def apply(
  kernelId: String,
  kernel: Kernel,
  kernelInfo: KernelInfo,
  progPath: String,
  options: ServerAppOptions = ServerAppOptions(),
  extraProgArgs: Seq[String] = Nil
): Unit
```
providing an id for the kernel, the instance of `InterpreterKernel` above,
some info about the kernel in a `KernelInfo`, the path
of the current program in `progPath`, and options passed
on the command line in `options` and `extraProgArgs`.
Lower level options to generate
[kernel specs](http://ipython.org/ipython-doc/dev/development/kernels.html#kernel-specs), or launch a kernel on a given
[connection file](http://ipython.org/ipython-doc/dev/development/kernels.html#connection-files)
are available too.

`jupyter-kernel` deals with the IPython messaging protocol,
and ZMQ connections, so that you don't have to.

It implements the `5.0` version of the IPython
[messaging protocol](http://ipython.org/ipython-doc/dev/development/messaging.html),
but it provides some (non exhaustive) fallbacks to deal with clients
using older versions.

Released under the LGPL version 3 license.
