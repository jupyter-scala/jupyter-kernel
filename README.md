# Jupyter kernel

*Helper library to write [Jupyter / IPython](http://ipython.org/) kernels on the JVM*

The Scala agnostic parts of [jupyter-scala](https://github.com/alexarchambault/jupyter-scala.git).

`jupyter-kernel` is a library that helps writing kernels for
[Jupyter / IPython](http://ipython.org/) on the JVM.
It mainly targets Scala for now, but could be used for other
languages as well in the future.

[![Build Status](https://travis-ci.org/alexarchambault/jupyter-kernel.svg?branch=master)](https://travis-ci.org/alexarchambault/jupyter-kernel)

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


It tries to implement the `5.0` version of the IPython
[messaging protocol](http://ipython.org/ipython-doc/dev/development/messaging.html),
but also attempts at providing some fallbacks to deal with clients
using older versions.

**More explanations and examples to come**

Released under the LGPL version 3 license.
