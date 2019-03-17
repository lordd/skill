/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.ecore

import scala.collection.JavaConversions.asScalaBuffer

import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.Declaration
import de.ust.skill.ir.Field
import de.ust.skill.ir.FieldLike
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.SetType
import de.ust.skill.ir.Type
import de.ust.skill.ir.VariableLengthArrayType
import de.ust.skill.main.HeaderInfo

/**
 * Fake Main implementation required to make trait stacking work.
 */
abstract class FakeMain extends GeneralOutputMaker { def make {} }

/**
 * Generates an ecore model that can be fed into EMF.
 *
 * @author Timm Felden
 */
class Main extends FakeMain
    with SpecificationMaker {

  lineLength = 80
  override def comment(d : Declaration) : String = ""
  override def comment(f : FieldLike) : String = ""

  /**
   * Translates the types into Ada types.
   */
  override protected def mapType(t : Type) : String = t match {
    case t : GroundType              ⇒ t.getSkillName
    case t : ConstantLengthArrayType ⇒ s"${mapType(t.getBaseType)}[${t.getLength}]"
    case t : VariableLengthArrayType ⇒ s"${mapType(t.getBaseType)}[]"
    case t : ListType                ⇒ s"list<${mapType(t.getBaseType)}>"
    case t : SetType                 ⇒ s"set<${mapType(t.getBaseType)}>"
    case t : MapType                 ⇒ t.getBaseTypes.mkString("map<", ", ", ">")
    case t                           ⇒ escaped(t.getName.capital)
  }

  /**
   * Provides the package prefix.
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix = ""

  override def setPackage(names : List[String]) {
    _packagePrefix = names.reduce(_ + "." + _)
  }

  override def setOption(option : String, value : String) {
    // no options
  }
  override def helpText : String = ""

  override def makeHeader(headerInfo : HeaderInfo) : String = ""

  /**
   * stats do not require any escaping
   */
  override def escaped(target : String) : String = target.replace(':', '_');

  override def customFieldManual : String = "(unsupported)"

  // unused
  override protected def defaultValue(f : Field) = throw new NoSuchMethodError

  override def packageDependentPathPostfix = if (packagePrefix.length > 0) {
    "/" + packagePrefix.replace(".", "/")
  } else {
    ""
  }
  override def defaultCleanMode = "file";
}
