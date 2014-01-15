/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013 University of Stuttgart                    **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.scala.internal.parsers

import java.io.PrintWriter
import de.ust.skill.generator.scala.GeneralOutputMaker

trait ByteReaderMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val out = open("internal/parsers/ByteReader.scala")
    //package & imports
    out.write(s"""package ${packagePrefix}internal.parsers

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Arrays

import scala.collection.mutable.Stack
import scala.util.parsing.input.Position
import scala.util.parsing.input.Reader

import ${packagePrefix}internal.UnexpectedEOF

case class ByteOffsetPosition(offset: Int) extends Position {
  final val line = 1
  def column = offset + 1
  def lineContents: String = ""
}

/**
 * @note the push/pop capabilities must be treated with care
 * @note the underlying implementation will very likely break backtracking capabilities of a combined parser
 *
 * @author Timm Felden
 */
final class ByteReader(file: FileChannel) extends Reader[Byte] {
  def this(path: Path) = this(Files.newByteChannel(path, StandardOpenOption.READ).asInstanceOf[FileChannel])

  /**
   * The memory mapped input buffer used to perform parse operations.
   */
  val input = file.map(MapMode.READ_ONLY, 0, file.size)

  private var positions = new Stack[Int]
  /**
   * saves the current position onto a stack and jumps to the argument position
   */
  def push(next: Long) { positions.push(input.position); input.position(next.toInt) }
  /**
   * returns to the last position saved
   */
  def pop: Unit = input.position(positions.pop)

  override def offset = input.position
  def position = input.position

  override def first: Byte = throw new NoSuchMethodError("unsupported operation")
  override def rest: ByteReader = throw new NoSuchMethodError("unsupported operation")
  def pos: Position = ByteOffsetPosition(offset)
  def atEnd = input.limit == input.position
  def has(n: Int): Boolean = input.limit >= (n + input.position)
  def minimumBytesToGo = input.limit - input.position

  override def drop(n: Int): ByteReader = {
    if (has(n))
      input.position(input.position + n);
    else
      throw UnexpectedEOF(s"@$$position while dropping $$n bytes", null)
    this
  }
  /**
   * takes n bytes from the stream; and returns them as an array, which has to be managed by the caller
   */
  private[parsers] def take(n: Int): Array[Byte] = {
    val rval = new Array[Byte](n)
    input.get(rval)
    rval
  }
  /**
   * fills the argument buffer with input from the stream
   *
   * @note does not rewind the buffer
   * @requires buffer needs to be array backed!
   */
  private[parsers] def fill(buffer: ByteBuffer): Unit = input.get(buffer.array)

  /**
   * like take, but creates a copy of the taken bytes to ensure correct usage
   */
  private[internal] def bytes(n: Int): Array[Byte] = take(n)

  def i8 = input.get
  def i16 = input.getShort
  def i32 = input.getInt
  def i64 = input.getLong

  def next: Byte = try {
    input.get
  } catch { case e: Exception ⇒ throw UnexpectedEOF("there's no next byte", e) }

  def v64: Long = {
    var count: Long = 0
    var rval: Long = 0
    var r: Long = next
    while (count < 8 && 0 != (r & 0x80)) {
      rval |= (r & 0x7f) << (7 * count);

      count += 1;
      r = next
    }
    rval = (rval | (count match {
      case 8 ⇒ r
      case _ ⇒ (r & 0x7f)
    }) << (7 * count));
    rval
  }

  def f32 = input.getFloat
  def f64 = input.getDouble
}
""")

    //class prefix
    out.close()
  }
}
