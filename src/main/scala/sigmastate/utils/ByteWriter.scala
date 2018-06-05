package sigmastate.utils

import java.util._

import sigmastate.SType
import sigmastate.Values.Value
import sigmastate.serialization.TypeSerializer
import sigmastate.utils.Extensions._

trait ByteWriter {
  def put(x: Byte): ByteWriter
  def putBoolean(x: Boolean): ByteWriter
  def putShort(x: Short): ByteWriter
  def putInt(x: Int): ByteWriter
  def putLong(x: Long): ByteWriter
  def putBytes(xs: Array[Byte]): ByteWriter
  def putOption[T](x: Option[T])(putValue: (ByteWriter, T) => Unit): ByteWriter
  def putType[T <: SType](x: T): ByteWriter
  def putValue[T <: SType](x: Value[T]): ByteWriter
  def toBytes: Array[Byte]
}

class ByteArrayWriter(b: ByteArrayBuilder) extends ByteWriter {
  @inline def put(x: Byte): ByteWriter = { b.append(x); this }
  @inline def putBoolean(x: Boolean): ByteWriter = { b.append(x); this }
  @inline def putShort(x: Short): ByteWriter = { b.append(x); this }
  @inline def putInt(x: Int): ByteWriter = { b.append(x); this }
  @inline def putLong(x: Long): ByteWriter = { b.append(x); this }

  /**
    * Encode signed Long using VLQ.
    *
    * @see [[https://en.wikipedia.org/wiki/Variable-length_quantity]]
    * @note The resulting varint uses ZigZag encoding, which is much more efficient.
    *       Have to be decoded '''only''' with [[ByteBufferReader.getSLong]]
    * @param x signed Long
    */
  @inline def putSLong(x: Long): ByteWriter = {
    // todo encode with ZigZag
    putULong(x)
  }

  /**
    * Encode unsigned Long using VLQ.
    *
    * @see [[https://en.wikipedia.org/wiki/Variable-length_quantity]]
    * @note Don't use it as the type for a negative number, the resulting varint is always ten
    *       bytes long – it is, effectively, treated like a very large unsigned integer.
    *       If you use [[putSLong]], the resulting varint uses ZigZag encoding,
    *       which is much more efficient.
    * @param x unsigned Long
    */
  @inline def putULong(x: Long): ByteWriter = {
    val buffer = new Array[Byte](10)
    var position = 0
    var value = x
    // should be fast if java -> scala conversion did not botched it
    // borrowed from http://github.com/google/protobuf/blob/a7252bf42df8f0841cf3a0c85fdbf1a5172adecb/java/core/src/main/java/com/google/protobuf/CodedOutputStream.java#L1387
    while (true) {
      if ((value & ~0x7FL) == 0) {
        buffer(position) = value.asInstanceOf[Byte]
        position += 1
        b.append(Arrays.copyOf(buffer, position))
        return this
      } else {
        buffer(position) = ((value.asInstanceOf[Int] & 0x7F) | 0x80).toByte
        position += 1
        value >>>= 7
      }
    }
    this
  }

  @inline def putBytes(xs: Array[Byte]): ByteWriter = { b.append(xs); this }
  @inline def putOption[T](x: Option[T])(putValue: (ByteWriter, T) => Unit): ByteWriter = { b.appendOption(x)(v => putValue(this, v)); this }
  @inline def putType[T <: SType](x: T): ByteWriter = { TypeSerializer.serialize(x, this); this }
  @inline def putValue[T <: SType](x: Value[T]): ByteWriter = { b.appendValue(x); this }
  @inline def toBytes: Array[Byte] = b.toBytes
}
