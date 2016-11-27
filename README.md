# Jupyter kernel

*Helper library to write [Jupyter / IPython](https://jupyter.org/) kernels on the JVM - Scala agnostic parts of [jupyter-scala](https://github.com/alexarchambault/jupyter-scala.git)*

[![Build Status](https://travis-ci.org/alexarchambault/jupyter-kernel.svg?branch=master)](https://travis-ci.org/alexarchambault/jupyter-kernel)
[![Maven Central](https://img.shields.io/maven-central/v/org.jupyter-scala/kernel_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/org.jupyter-scala/kernel_2.11)
[![Scaladoc](https://javadoc-badge.appspot.com/org.jupyter-scala/kernel_2.11.svg?label=scaladoc)](https://javadoc-badge.appspot.com/org.jupyter-scala/kernel_2.11)

`jupyter-kernel` is a library that helps writing kernels for
[Jupyter / IPython](https://jupyter.org/) on the JVM.
It mainly targets Scala for now, but could be used for other
languages as well.
It tries to implement the `5.0` version of the IPython
[messaging protocol](https://jupyter-client.readthedocs.io/en/latest/messaging.html).

### Limitations

It currently has a poor test coverage, although that point should be easy to address.

### Notice

Released under the LGPL version 3 license.
