package jupyter.api

import internals.Base64._

trait Display {

  def display(items: (String, String)*): Unit

  final def html(html: String): Unit =
    display("text/html" -> html)
  final def markdown(md: String): Unit =
    display("text/markdown" -> md)
  final def md(md: String): Unit =
    markdown(md)
  final def svg(svg: String): Unit =
    display("image/svg+xml" -> svg)
  final def png(data: Array[Byte]): Unit =
    display("image/png" -> data.toBase64)
  final def png(data: java.awt.image.BufferedImage): Unit = {
    val baos = new java.io.ByteArrayOutputStream
    javax.imageio.ImageIO.write(data, "png", baos)
    png(baos.toByteArray)
  }
  final def jpg(data: Array[Byte]): Unit =
    display("image/jpeg" -> data.toBase64)
  final def jpg(data: java.awt.image.BufferedImage): Unit = {
    val baos = new java.io.ByteArrayOutputStream
    javax.imageio.ImageIO.write(data, "jpg", baos)
    jpg(baos.toByteArray)
  }
  final def latex(latex: String): Unit =
    display("text/latex" -> latex)
  final def pdf(data: Array[Byte]): Unit =
    display("application/pdf" -> data.toBase64)
  final def javascript(code: String): Unit =
    display("application/javascript" -> code)
  final def js(code: String): Unit =
    javascript(code)
}