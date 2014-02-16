/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013 University of Stuttgart                    **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.scala.internal.pool

import java.io.PrintWriter
import scala.collection.JavaConversions.asScalaBuffer
import de.ust.skill.ir.ContainerType
import de.ust.skill.ir.Declaration
import de.ust.skill.ir.Field
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.Type
import de.ust.skill.generator.scala.GeneralOutputMaker
import de.ust.skill.ir.MapType
import de.ust.skill.ir.SingleBaseTypeContainer
import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.restriction.SingletonRestriction
import de.ust.skill.ir.VariableLengthArrayType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.SetType

/**
 * Creates storage pools for declared types.
 *
 * @author Timm Felden
 */
trait DeclaredPoolsMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    IR.foreach({ d ⇒
      makePool(open("internal/pool/"+d.getName()+"StoragePool.scala"), d)
    })
  }

  /**
   * Maps types to their "TypeInfo" correspondents.
   */
  private def mapTypeInfo(t: Type): String = {
    def mapGroundType(t: Type) = t.getSkillName() match {
      case "annotation" ⇒ "AnnotationInfo"
      case "bool"       ⇒ "BoolInfo"
      case "i8"         ⇒ "I8Info"
      case "i16"        ⇒ "I16Info"
      case "i32"        ⇒ "I32Info"
      case "i64"        ⇒ "I64Info"
      case "v64"        ⇒ "V64Info"
      case "f32"        ⇒ "F32Info"
      case "f64"        ⇒ "F64Info"
      case "string"     ⇒ "StringInfo"

      case s            ⇒ s"""NamedUserType("$s")"""
    }

    t match {
      case t: GroundType              ⇒ mapGroundType(t)
      case t: ConstantLengthArrayType ⇒ s"new ConstantLengthArrayInfo(${t.getLength}, ${mapGroundType(t.getBaseType)})"
      case t: VariableLengthArrayType ⇒ s"new VariableLengthArrayInfo(${mapGroundType(t.getBaseType)})"
      case t: ListType                ⇒ s"new ListInfo(${mapGroundType(t.getBaseType)})"
      case t: SetType                 ⇒ s"new SetInfo(${mapGroundType(t.getBaseType)})"
      case t: MapType                 ⇒ s"new MapInfo(List[TypeInfo](${t.getBaseTypes().map(mapGroundType).mkString(",")}))"
      case t: Declaration             ⇒ s"""NamedUserType("${t.getSkillName}")"""
    }
  }

  /**
   * This method creates a type check for deserialization.
   */
  private def checkType(f: Field) = f.getType() match {
    case t: GroundType ⇒ t.getSkillName() match {
      case "annotation" ⇒ "f.t == AnnotationInfo"
      case "bool"       ⇒ "f.t == BoolInfo"
      case "i8"         ⇒ "f.t == I8Info"
      case "i16"        ⇒ "f.t == I16Info"
      case "i32"        ⇒ "f.t == I32Info"
      case "i64"        ⇒ "f.t == I64Info"
      case "v64"        ⇒ "f.t == V64Info"
      case "f32"        ⇒ "f.t == F32Info"
      case "f64"        ⇒ "f.t == F64Info"
      case "string"     ⇒ "f.t == StringInfo"
      case s            ⇒ throw new Error(s"not yet implemented: $s")
    }
    // compound types use the string representation to check the type; note that this depends on IR.toString-methods
    case t: ContainerType ⇒ s"""f.t.toString.equals("$t")"""

    case t: Declaration ⇒
      s"""f.t.isInstanceOf[UserType] && f.t.asInstanceOf[UserType].name.equals("${t.getSkillName()}")"""

    // this should be unreachable; it might be reachable if IR changed
    case t ⇒ throw new Error(s"not yet implemented: ${t.getName()}")
  }

  private def makeReadCode(f: Field): String = f.getType match {
    case t: GroundType if t.isInteger() ⇒ s"""val fieldData = fieldParser.read${t.getSkillName().capitalize}s(dynamicSize, f.dataChunks)
          val fields = iterator
          for (i ← 0 until fieldData.size)
            fields.next.${escaped(f.getName)}_=(fieldData(i))"""
    case t ⇒ s"""${makeReadFunctionCall(t)}

          iterator.foreach(_.${escaped(f.getName)}_=(it.next))"""
  }

  private def makeReadFunctionCall(t: Type): String = t match {
    case t: GroundType ⇒
      s"val it = fieldParser.read${t.getSkillName().capitalize}s(dynamicSize, f.dataChunks)"

    case t: Declaration ⇒
      s"""val d = new Array[_root_.${packagePrefix}${t.getCapitalName()}](dynamicSize.toInt)
          fieldParser.readUserRefs("${t.getSkillName()}", d, f.dataChunks)
          val it = d.iterator"""

    case t: MapType ⇒ s"""val it = fieldParser.readMaps[${mapType(t)}](
            f.t.asInstanceOf[MapInfo],
            dynamicSize,
            f.dataChunks
          )"""

    case t: SingleBaseTypeContainer ⇒
      s"""val it = fieldParser.read${t.getClass().getSimpleName().replace("Type", "")}s[${mapType(t.getBaseType())}](
            f.t.asInstanceOf[${t.getClass().getSimpleName().replace("Type", "Info")}],
            dynamicSize,
            f.dataChunks
          )"""
  }

  /**
   * Make a pool for d.
   */
  private def makePool(out: PrintWriter, d: Declaration) {
    val name = d.getName
    val sName = name.toLowerCase()
    val fields = d.getFields().toList
    val isSingleton = !d.getRestrictions.collect { case r: SingletonRestriction ⇒ r }.isEmpty

    ////////////
    // HEADER //
    ////////////

    out.write(s"""package ${packagePrefix}internal.pool

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import ${packagePrefix}api._
import ${packagePrefix}internal._
import ${packagePrefix}internal.parsers.FieldParser
import ${packagePrefix}internal.types._

final class ${name}StoragePool(state: SerializableState) extends ${
      d.getSuperType() match {
        case null ⇒
          s"""BasePool[_root_.$packagePrefix$name](
  "${d.getSkillName}",
  HashMap[String, FieldDeclaration](
    ${
            (
              for (f ← d.getFields())
                yield s""""${f.getSkillName()}" -> new FieldDeclaration(${mapTypeInfo(f.getType)}, "${f.getSkillName()}", -1)"""
            ).mkString("", ",\n    ", "")
          }
  ),
  Array[_root_.$packagePrefix$name]()
)"""

        case s ⇒ {
          val base = s"_root_.$packagePrefix${d.getBaseType().getName()}"
          s"""SubPool[_root_.$packagePrefix$name, $base](
  "${d.getSkillName}",
  HashMap[String, FieldDeclaration](
  ),
  state.${s.getName.capitalize}
)"""
        }
      }
    } with SkillState.${name}Access {

  @inline override def newInstance = new _root_.${packagePrefix}internal.types.$name
""")

    ////////////
    // ACCESS //
    ////////////

    if (isSingleton) {
      // this is a singleton
      out.write(s"""
  override def get: $name = staticData(0)""")

    } else {
      val applyCallArguments = d.getAllFields().filter { f ⇒ !f.isConstant && !f.isIgnored }.map({
        f ⇒ s"${f.getName().capitalize}: ${mapType(f.getType())}"
      }).mkString(", ")

      out.write(s"""
  override def all = iterator
  override def allInTypeOrder = typeOrderIterator
  override def apply($applyCallArguments) = add$name(new _root_.${packagePrefix}internal.types.$name($applyCallArguments))
""")
    }

    ///////////////
    // ITERATORS //
    ///////////////

    out.write(s"""
  override def iterator = ${
      if (null == d.getSuperType) s"""data.iterator ++ newDynamicInstances"""
      else s"blockInfos.foldRight(newDynamicInstances) { (block, iter) ⇒ basePool.data.view(block.bpsi.toInt-1, (block.bpsi + block.count).toInt-1).asInstanceOf[Iterable[$name]].iterator ++ iter }"
    }

  override def typeOrderIterator = subPools.collect {
    // @note: you can ignore the type erasure warning, because the generators invariants guarantee type safety
    case p: KnownPool[_, $name] @unchecked ⇒ p
  }.foldLeft(staticInstances)(_ ++ _.staticInstances)

  override def staticInstances = staticData.iterator ++ newObjects.iterator
  override def newDynamicInstances = subPools.collect {
    // @note: you can ignore the type erasure warning, because the generators invariants guarantee type safety
    case p: KnownPool[_, $name] @unchecked ⇒ p
  }.foldLeft(newObjects.iterator)(_ ++ _.newObjects.iterator)

  /**
   * the number of static instances loaded from the file
   */
  ${
      if (isSingleton) s"""private val staticData = Array[_root_.${packagePrefix}internal.types.$name](newInstance);"""
      else s"""private var staticData = Array[_root_.${packagePrefix}internal.types.$name]();"""
    }
  /**
   * the static size is thus the number of static instances plus the number of new objects
   */
  override def staticSize: Long = staticData.size + newObjects.length

  /**
   * construct instances of the pool in post-order, i.e. bottom-up
   */
  final override def constructPool() {
    ${
      if (isSingleton) "// the singleton instance is always present"
      else s"""// construct data in a bottom up order
    subPools.collect { case p: KnownPool[_, _] ⇒ p }.foreach(_.constructPool)
    val staticDataConstructor = new ArrayBuffer[_root_.${packagePrefix}internal.types.$name]
    for (b ← blockInfos) {
      val from: Int = b.bpsi.toInt - 1
      val until: Int = b.bpsi.toInt + b.count.toInt - 1
      for (i ← from until until)
        if (null == data(i)) {
          val next = new _root_.${packagePrefix}internal.types.$name
          next.setSkillID(i + 1)
          staticDataConstructor += next
          data(i) = next
        }
    }
    staticData = staticDataConstructor.toArray"""
    }
  }

  // set eager fields of data instances
  override def readFields(fieldParser: FieldParser) {
    subPools.collect { case p: KnownPool[_, _] ⇒ p }.foreach(_.readFields(fieldParser))

    for ((name, f) ← fields if !f.dataChunks.isEmpty) {
      (name: @switch) match {""")

    // parse known fields
    fields.foreach({ f ⇒
      if (!f.isIgnored()) {
        val name = f.getName()
        if (f.isConstant) {
          // constant fields are not directly deserialized, but they need to be checked for the right value
          out.write(s"""
        // const ${f.getType().getSkillName()} $name
        case "${f.getSkillName()}" ⇒
         if(f.t.asInstanceOf[ConstantIntegerInfo[_]].value != ${f.constantValue})
            throw new SkillException("Constant value differed.")
""")

        } else if (f.isAuto) {
          // auto fields must not be part of the serialized data
          out.write(s"""
        // auto ${f.getType.getSkillName} $name
        case "${f.getSkillName()}" ⇒ if(!f.dataChunks.isEmpty)
          throw new SkillException("Found field data for auto field ${d.getName()}.$name")
""")

        } else {
          // the ordinary field case
          out.write(s"""
        // ${f.getType.getSkillName} $name
        case "${f.getSkillName()}" ⇒ try {
          ${makeReadCode(f)}
        } catch {
          case e: UnexpectedEOF ⇒ throw new UnexpectedEOF("Failed to parse field data of (${f.getType().getSkillName()}) ${d.getName()}.$name", e)
        }
""")
        }
      }
    })

    // note: the add method will get more complex as soon as restrictions are added, e.g. in the context of @unique
    out.write(s"""
        // TODO delegate type error detection to the field parser
        case _ ⇒ // TODO generic fields
      }
    }
  }

  private[internal] def add$name(obj: ${packagePrefix}internal.types.$name): $packagePrefix$name = {
    newObjects.append(obj);
    obj
  }

  override def prepareSerialization(σ: SerializableState) {
  }
}
""")
    out.close()
  }
}
