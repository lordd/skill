/*  ___ _  ___ _ _                                                            *\
 * / __| |/ (_) | |       Your SKilL Scala Binding                            *
 * \__ \ ' <| | | |__     generated: 19.11.2014                               *
 * |___/_|\_\_|_|____|    by: Timm Felden                                     *
\*                                                                            */
package de.ust.skill.generator.genericBinding.internal

import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.WrappedArray
import scala.concurrent.ExecutionContext

import _root_.de.ust.skill.generator.genericBinding.internal.streams.FileOutputStream
import _root_.de.ust.skill.generator.genericBinding.internal.streams.MappedOutStream

/**
 * Holds state of a write operation.
 *
 * @see SKilL §6
 * @author Timm Felden
 */
private[internal] final class StateWriter(state : State, out : FileOutputStream) extends SerializationFunctions(state) {
  import SerializationFunctions._

  // make lbpsi map, update data map to contain dynamic instances and create serialization skill IDs for serialization
  // index → bpsi
  //@note pools.par would not be possible if it were an actual map:)
  val lbpsiMap = new Array[Long](state.pools.length)
  state.pools.par.foreach {
    case p : BasePool[_] ⇒
      makeLBPSIMap(p, lbpsiMap, 1, { s ⇒ state.poolByName(s).staticSize })
      p.compress(lbpsiMap)
    case _ ⇒
  }

  /**
   * ****************
   * PHASE 3: WRITE *
   * ****************
   */
  // write string block
  state.String.asInstanceOf[StringPool].prepareAndWrite(out, this)

  // write count of the type block
  v64(state.pools.size, out)

  // calculate offsets
  // TODO this code can be simplified a lot using ".par 
  val offsets = new HashMap[StoragePool[_ <: SkillType, _ <: SkillType], HashMap[FieldDeclaration[_], Future[Long]]]
  for (p ← state.pools) {
    val vs = new HashMap[FieldDeclaration[_], Future[Long]]
    for (f ← p.fields) {
      val v = new FutureTask(new Callable[Long]() {
        def call : Long = FieldOffsetCalculator.offset(p, f)
      })
      vs.put(f, v)
      ExecutionContext.Implicits.global.execute(v)
    }
    offsets.put(p, vs)
  }

  // create type definitions
  // @note performance hack: requires at least 1 instance in order to work correctly
  @inline def genericPutField[T](p : StoragePool[_ <: SkillType, _ <: SkillType], f : FieldDeclaration[T], dataChunk : MappedOutStream) {
    f.t match {
      case I8         ⇒ for (i ← p) dataChunk.i8(i.get(f).asInstanceOf[Byte])
      case I16        ⇒ for (i ← p) dataChunk.i16(i.get(f).asInstanceOf[Short])
      case I32        ⇒ for (i ← p) dataChunk.i32(i.get(f).asInstanceOf[Int])
      case I64        ⇒ for (i ← p) dataChunk.i64(i.get(f).asInstanceOf[Long])
      case V64        ⇒ for (i ← p) dataChunk.v64(i.get(f).asInstanceOf[Long])

      case StringType(_) ⇒ for (i ← p) string(i.get(f).asInstanceOf[String], dataChunk)

      case other ⇒
        val den = typeToSerializationFunction(other); for (i ← p) den(i.get(f), dataChunk)
    }
  }

  case class Task[B <: SkillType](val p : StoragePool[_ <: B, B], val f : FieldDeclaration[_], val begin : Long, val end : Long);
  val data = new ArrayBuffer[Task[_ <: SkillType]];
  var offset = 0L
  for (p ← state.pools) {
    val vs = offsets(p)
    val fields = p.fields.view(1, p.fields.size) // ignore field 0 (skillID)
    string(p.name, out)
    val LCount = p.blockInfos.last.count
    out.v64(LCount)
    restrictions(p, out)
    p.superName match {
      case Some(sn) ⇒
        string(sn, out)
        if (0L != LCount)
          out.v64(lbpsiMap(p.poolIndex.toInt))

      case None ⇒
        out.i8(0.toByte)
    }

    out.v64(fields.size)
    for (f ← fields) {
      out.v64(f.index)
      string(f.name, out)
      writeType(f.t, out)
      restrictions(f, out)
      val end = offset + vs(f).get
      out.v64(end)
      data += Task(p.asInstanceOf[StoragePool[SkillType, SkillType]], f, offset, end)
      offset = end
    }
  }

  // write field data
  val barrier = new Semaphore(0)
  val baseOffset = out.position
  for ((Task(p, f, begin, end)) ← data) {
    val dataChunk = out.map(baseOffset, begin, end)
    ExecutionContext.Implicits.global.execute(new Runnable {
      override def run = {
        p match {

          case _ ⇒ genericPutField(p, f, dataChunk)
        }
        barrier.release(1)
      }
    }
    )
  }
  barrier.acquire(data.size)
  out.close
}
