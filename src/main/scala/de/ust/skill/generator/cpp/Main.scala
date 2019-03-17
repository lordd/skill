/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.cpp

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.Declaration
import de.ust.skill.ir.Field
import de.ust.skill.ir.FieldLike
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.InterfaceType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.SetType
import de.ust.skill.ir.Type
import de.ust.skill.ir.UserType
import de.ust.skill.ir.VariableLengthArrayType
import de.ust.skill.main.HeaderInfo

/**
 * Fake Main implementation required to make trait stacking work.
 */
abstract class FakeMain extends GeneralOutputMaker { def make {} }

/**
 * A generator turns a set of skill declarations into a scala interface providing means of manipulating skill files
 * containing instances of the respective UserTypes.
 *
 * @author Timm Felden
 */
final class Main extends FakeMain
  with FieldDeclarationsMaker
  with SkillFileMaker
  with StringKeeperMaker
  with PoolsMaker
  with TypesMaker {

  lineLength = 120
  override def comment(d : Declaration) : String = d.getComment.format("/**\n", "     * ", lineLength, "     */\n    ")
  override def comment(f : FieldLike) : String = f.getComment.format("/**\n", "         * ", lineLength, "         */\n        ")

  override def packageDependentPathPostfix = ""
  override def defaultCleanMode = "file";

  /**
   * Translates types into scala type names.
   */
  override def mapType(t : Type) : String = t match {
    case t : GroundType ⇒ t.getName.lower match {
      case "annotation" ⇒ "::skill::api::Object*"

      case "bool"       ⇒ "bool"

      case "i8"         ⇒ "int8_t"
      case "i16"        ⇒ "int16_t"
      case "i32"        ⇒ "int32_t"
      case "i64"        ⇒ "int64_t"
      case "v64"        ⇒ "int64_t"

      case "f32"        ⇒ "float"
      case "f64"        ⇒ "double"

      case "string"     ⇒ "::skill::api::String"
    }

    case t : ConstantLengthArrayType ⇒ s"::skill::api::Array<${mapType(t.getBaseType())}>*"
    case t : VariableLengthArrayType ⇒ s"::skill::api::Array<${mapType(t.getBaseType())}>*"
    case t : ListType                ⇒ s"::skill::api::Array<${mapType(t.getBaseType())}>*"
    case t : SetType                 ⇒ s"::skill::api::Set<${mapType(t.getBaseType())}>*"
    case t : MapType                 ⇒ t.getBaseTypes().map(mapType).reduceRight((k, v) ⇒ s"::skill::api::Map<$k, $v>*")

    case t : Declaration             ⇒ s"$packageName::${name(t)}*"

    case _                           ⇒ throw new IllegalStateException(s"Unknown type $t")
  }

  override protected def unbox(t : Type) : String = t match {

    case t : GroundType ⇒ t.getName.lower match {
      case "bool" ⇒ "boolean"
      case "v64"  ⇒ "i64"
      case t      ⇒ t;
    }

    case t : ConstantLengthArrayType ⇒ "array"
    case t : VariableLengthArrayType ⇒ "list"
    case t : ListType                ⇒ "list"
    case t : SetType                 ⇒ "set"
    case t : MapType                 ⇒ "map"

    case t : Declaration             ⇒ "annotation"

    case _                           ⇒ throw new IllegalStateException(s"Unknown type $t")
  }

  /**
   * creates argument list of a constructor call, not including potential skillID or braces
   */
  override protected def makeConstructorArguments(t : UserType) = (
    for (f ← t.getAllFields if !(f.isConstant || f.isIgnored))
      yield s"${escaped(f.getName.camel)} : ${mapType(f.getType())}").mkString(", ")
  override protected def appendConstructorArguments(t : UserType) = {
    val r = t.getAllFields.filterNot { f ⇒ f.isConstant || f.isIgnored }
    if (r.isEmpty) ""
    else r.map({ f ⇒ s"${escaped(f.getName.camel)} : ${mapType(f.getType())}" }).mkString(", ", ", ", "")
  }

  override def makeHeader(headerInfo : HeaderInfo) : String = headerInfo.format(this, "/*", "*\\", " *", "* ", "\\*", "*/")

  /**
   * provides the package prefix
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix : String = null

  override def setPackage(names : List[String]) {
    _packagePrefix = names.foldRight("")(_ + "." + _)
  }

  override def setOption(option : String, value : String) {
    option match {
      case "revealskillid"   ⇒ revealSkillID = ("true".equals(value))
      case "interfacechecks" ⇒ interfaceChecks = ("true".equals(value))
      case unknown           ⇒ sys.error(s"unkown Argument: $unknown")
    }
  }
  override def helpText : String = """
revealSkillID     true/false  if set to true, the generated binding will reveal SKilL IDs in the API
interfaceChecks   true/false  if set to true, the generated API will contain is[[interface]] methods
"""

  override def customFieldManual : String = """
!include string+    Argument strings are added to the head of the generated file and included using
                    <> around the strings content.
!default string     Text to be inserted as replacement for default initialization."""

  override protected def defaultValue(f : Field) =
    f.getType match {
      case t : GroundType ⇒ t.getSkillName() match {
        case "i8" | "i16" | "i32" | "i64" | "v64" ⇒ "0"
        case "f32" | "f64"                        ⇒ "0.0"
        case "bool"                               ⇒ "false"
        case _                                    ⇒ "nullptr"
      }

      case t : UserType ⇒ "nullptr"

      case _            ⇒ "nullptr"
    }

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   */
  private val escapeCache = new HashMap[String, String]();
  final def escaped(target : String) : String = escapeCache.getOrElse(target, {
    val result = EscapeFunction(target)
    escapeCache(target) = result
    result
  })

  protected def filterIntarfacesFromIR() {
    // find implementers
    val ts = types.removeTypedefs()
    for (t ← ts.getUsertypes) {
      val is : HashSet[InterfaceType] = t.getSuperInterfaces.flatMap(recursiveSuperInterfaces(_, new HashSet[InterfaceType])).to
      interfaceCheckImplementations(t.getSkillName) = is.map(insertInterface(_, t))
    }
  }
  private def insertInterface(i : InterfaceType, target : UserType) : String = {
    // register a potential implementation for the target type and interface
    i.getBaseType match {
      case b : UserType ⇒
        interfaceCheckMethods.getOrElseUpdate(b.getSkillName, new HashSet[String]) += i.getName.capital()
      case _ ⇒
        interfaceCheckMethods.getOrElseUpdate(target.getBaseType.getSkillName, new HashSet[String]) += i.getName.capital()
    }
    // return the name to be used
    i.getName.capital
  }
  private def recursiveSuperInterfaces(i : InterfaceType, r : HashSet[InterfaceType]) : HashSet[InterfaceType] = {
    r += i
    for (s ← i.getSuperInterfaces) {
      recursiveSuperInterfaces(s, r)
    }
    r
  }

  protected def writeField(d : UserType, f : Field) : String = {
    val fName = escaped(f.getName.camel)
    if (f.isConstant()) { "// constants do not write individual field data" }
    else {
      f.getType match {
        case t : GroundType ⇒ t.getSkillName match {
          case "annotation" | "string" ⇒ s"for(i ← outData) ${f.getType.getSkillName}(i.$fName, dataChunk)"
          case _                       ⇒ s"for(i ← outData) dataChunk.${f.getType.getSkillName}(i.$fName)"

        }

        case t : Declaration ⇒ s"""for(i ← outData) userRef(i.$fName, dataChunk)"""

        case t : ConstantLengthArrayType ⇒ s"for(i ← outData) writeConstArray(${
          t.getBaseType() match {
            case t : Declaration ⇒ s"userRef[${mapType(t)}]"
            case b               ⇒ b.getSkillName()
          }
        })(i.$fName, dataChunk)"
        case t : VariableLengthArrayType ⇒ s"for(i ← outData) writeVarArray(${
          t.getBaseType() match {
            case t : Declaration ⇒ s"userRef[${mapType(t)}]"
            case b               ⇒ b.getSkillName()
          }
        })(i.$fName, dataChunk)"
        case t : SetType ⇒ s"for(i ← outData) writeSet(${
          t.getBaseType() match {
            case t : Declaration ⇒ s"userRef[${mapType(t)}]"
            case b               ⇒ b.getSkillName()
          }
        })(i.$fName, dataChunk)"
        case t : ListType ⇒ s"for(i ← outData) writeList(${
          t.getBaseType() match {
            case t : Declaration ⇒ s"userRef[${mapType(t)}]"
            case b               ⇒ b.getSkillName()
          }
        })(i.$fName, dataChunk)"

        case t : MapType ⇒ locally {
          s"for(i ← outData) ${
            t.getBaseTypes().map {
              case t : Declaration ⇒ s"userRef[${mapType(t)}]"
              case b               ⇒ b.getSkillName()
            }.reduceRight { (t, v) ⇒
              s"writeMap($t, $v)"
            }
          }(i.$fName, dataChunk)"
        }
      }
    }
  }
}

object EscapeFunction {
  def apply(target : String) : String = target match {
    //keywords get a suffix "_", because that way at least auto-completion will work as expected
    case "auto" | "const" | "double" | "float" | "int" | "short" | "struct" | "unsigned" | "break" | "continue"
      | "else" | "for" | "long" | "signed" | "switch" | "void" | "case" | "default" | "enum" | "goto" | "register"
      | "sizeof" | "typedef" | "volatile" | "char" | "do" | "extern" | "if" | "return" | "static" | "union" | "while"
      | "asm" | "dynamic_cast" | "namespace" | "reinterpret_cast" | "try" | "bool" | "explicit" | "new" | "static_cast"
      | "typeid" | "catch" | "false" | "operator" | "template" | "typename" | "class" | "friend" | "private" | "this"
      | "using" | "const_cast" | "inline" | "public" | "throw" | "virtual" | "delete" | "mutable" | "protected"
      | "true" | "wchar_t" | "and" | "bitand" | "compl" | "not_eq" | "or_eq" | "xor_eq" | "and_eq" | "bitor" | "not"
      | "or" | "xor" | "cin" | "endl" | "INT_MIN" | "iomanip" | "main" | "npos" | "std" | "cout" | "include"
      | "INT_MAX" | "iostream" | "MAX_RAND" | "NULL" | "string" ⇒ s"_$target"

    case t if t.forall(c ⇒ '_' == c || Character.isLetterOrDigit(c)) ⇒ t

    case _ ⇒ target.map {
      case 'Z' ⇒ "ZZ"
      case c if '_' == c || Character.isLetterOrDigit(c) ⇒ "" + c
      case c ⇒ f"Z$c%04X"
    }.mkString
  }
}
