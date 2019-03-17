/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.jforeign

import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.bufferAsJavaList
import scala.collection.mutable.ListBuffer

import de.ust.skill.ir.ListType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.SetType
import de.ust.skill.ir.Type
import de.ust.skill.ir.TypeContext
import javassist.ClassPool
import javassist.NotFoundException
import sun.reflect.generics.tree.ArrayTypeSignature
import sun.reflect.generics.tree.BooleanSignature
import sun.reflect.generics.tree.BottomSignature
import sun.reflect.generics.tree.ByteSignature
import sun.reflect.generics.tree.CharSignature
import sun.reflect.generics.tree.ClassSignature
import sun.reflect.generics.tree.ClassTypeSignature
import sun.reflect.generics.tree.DoubleSignature
import sun.reflect.generics.tree.FloatSignature
import sun.reflect.generics.tree.FormalTypeParameter
import sun.reflect.generics.tree.IntSignature
import sun.reflect.generics.tree.LongSignature
import sun.reflect.generics.tree.MethodTypeSignature
import sun.reflect.generics.tree.ShortSignature
import sun.reflect.generics.tree.SimpleClassTypeSignature
import sun.reflect.generics.tree.TypeVariableSignature
import sun.reflect.generics.tree.VoidDescriptor
import sun.reflect.generics.tree.Wildcard

/**
 * This is a nasty stateful visitor (thanks to the lack of generic arguments to the visit methods).
 */
class SignatureVisitor(tc: TypeContext, classPaths: List[String], mapInUserContext: String ⇒ Option[Type])
    extends sun.reflect.generics.visitor.Visitor[Option[Type]] {

  var topLevel: Boolean = true
  var typeargs = new ListBuffer[Type]
  var result: Option[Type] = None

  val utilPool = new ClassPool(true)
  classPaths.foreach(utilPool.appendClassPath)
  utilPool.importPackage("java.util.*")
  val listt = utilPool.get("java.util.List")
  val sett = utilPool.get("java.util.Set")
  val mapt = utilPool.get("java.util.Map")

  override def getResult(): Option[Type] = { result }

  override def visitClassSignature(cs: ClassSignature): Unit = {
    cs.getSuperclass.accept(this)
  }

  override def visitClassTypeSignature(ct: ClassTypeSignature): Unit = {
    ct.getPath.foreach { x => x.accept(this) }
  }

  override def visitSimpleClassTypeSignature(sct: SimpleClassTypeSignature): Unit = {
    if (topLevel) {
      topLevel = false
      val clazz = utilPool.get(sct.getName)
      sct.getTypeArguments.foreach { _.accept(this) }
      if (typeargs.size < 1) {
        result = None
        return
      }

      if (clazz.subtypeOf(listt)) {
        assert(typeargs.size == 1)
        result = Some(ListType.make(tc, typeargs.get(0)))
      } else if (clazz.subtypeOf(sett)) {
        assert(typeargs.size == 1)
        result = Some(SetType.make(tc, typeargs.get(0)))
      } else if (clazz.subtypeOf(mapt)) {
        if (typeargs.size < 2) result = None
        else result = Some(MapType.make(tc, typeargs))
      }
    } else {
      try {
        val clazz = utilPool.get(sct.getName)
        if (clazz.subtypeOf(mapt)) {
          sct.getTypeArguments.foreach { _.accept(this) }
          return
        }
      } catch {
        case e: NotFoundException ⇒ // do nothing!
      }
      val ta = mapInUserContext(sct.getName)
      typeargs += ta.getOrElse(throw new RuntimeException(s"Class not found: ${sct.getName} (used as type argument)"))
    }
  }

  override def visitArrayTypeSignature(a: ArrayTypeSignature): Unit = {}
  override def visitTypeVariableSignature(tv: TypeVariableSignature): Unit = {
    // we do nothing here, because we cannot deal with a type variable as argument
  }
  override def visitWildcard(w: Wildcard): Unit = {}
  override def visitMethodTypeSignature(ms: MethodTypeSignature): Unit = {}
  override def visitFormalTypeParameter(ftp: FormalTypeParameter): Unit = {}
  override def visitBottomSignature(b: BottomSignature): Unit = {}
  override def visitByteSignature(b: ByteSignature): Unit = {}
  override def visitBooleanSignature(b: BooleanSignature): Unit = {}
  override def visitShortSignature(s: ShortSignature): Unit = {}
  override def visitCharSignature(c: CharSignature): Unit = {}
  override def visitIntSignature(i: IntSignature): Unit = {}
  override def visitLongSignature(l: LongSignature): Unit = {}
  override def visitFloatSignature(f: FloatSignature): Unit = {}
  override def visitDoubleSignature(d: DoubleSignature): Unit = {}
  override def visitVoidDescriptor(v: VoidDescriptor): Unit = {}

}
