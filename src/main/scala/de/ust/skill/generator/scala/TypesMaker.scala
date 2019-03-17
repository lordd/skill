/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.scala

import scala.collection.JavaConverters._

import de.ust.skill.ir.Declaration
import de.ust.skill.ir.Field
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.UserType
import de.ust.skill.ir.WithFields
import de.ust.skill.ir.restriction.FloatRangeRestriction
import de.ust.skill.ir.restriction.IntRangeRestriction
import de.ust.skill.ir.restriction.MonotoneRestriction
import de.ust.skill.ir.InterfaceType
import de.ust.skill.ir.FieldLike
import de.ust.skill.io.PrintWriter

trait TypesMaker extends GeneralOutputMaker {

  @inline def fieldName(implicit f : Field) : String = escaped(f.getName.camel())
  @inline def localFieldName(implicit f : Field) : String = escaped("_" + f.getName.camel())
  @inline def fieldAssignName(implicit f : Field) : String = escaped(f.getName.camel() + "_=")
  @inline def introducesStateRef(t : UserType) : Boolean = t.hasDistributedField() && (
      null==t.getSuperType() || !t.getSuperType.hasDistributedField()
    )

  abstract override def make {
    super.make
    
    // requires knowledge about distributed fields inherited from interfaces
    val flatIR = this.types.removeSpecialDeclarations.getUsertypes.asScala.toArray

    val packageName = if(this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.')+1)
    else this.packageName;
    
    // create one file for all base-less interfaces
    createAnnotationInterfaces;

    // create one file for each type hierarchy to help parallel builds
    for(base <- IR if null==base.getSuperType){

      val out = files.open(s"TypesOf${base.getName.capital}.scala")

      //package
      out.write(s"""package ${this.packageName}

import de.ust.skill.common.scala.SkillID
import de.ust.skill.common.scala.api.SkillObject
import de.ust.skill.common.scala.api.Access
import de.ust.skill.common.scala.api.UnknownObject
${(for(t ← IR if t.getBaseType == base;
        c <- t.getCustomizations.asScala if c.language.equals("scala");
        is <- c.getOptions.asScala.toMap.get("import").toArray;
        i  <- is.asScala
        ) yield s"import $i;\n").mkString}""")
    


    for (t ← IR if t.getBaseType == base) {
      val flatType = flatIR.find(_.getName == t.getName).get
      
      val fields = t.getAllFields.asScala.filter(!_.isConstant)
      val relevantFields = fields.filter(!_.isIgnored)

      //class declaration
      out.write(s"""
${
        comment(t)
}sealed class ${name(t)} (_skillID : SkillID${
  if(flatType.hasDistributedField())
    (", " + (
      if(introducesStateRef(t)) "val "
      else ""
    ) + "__state : api.SkillFile")
  else ""
}) extends ${
        if (null != t.getSuperType()) s"${name(t.getSuperType)}(_skillID${
          if(flatType.getSuperType.hasDistributedField())", __state"
          else ""
        })"
        else "SkillObject(_skillID)"
      }${
  (for(s <- t.getSuperInterfaces.asScala)
    yield " with " + name(s)).mkString
} {${
	  if(t.getSuperType == null) s"""

  //reveal skill id
  ${if(revealSkillID)"" else s"protected[${packageName}] "}final def getSkillID = skillID
"""
	  else ""
	}""")

      // constructor
	if(!relevantFields.isEmpty){
    	out.write(s"""
  private[$packageName] def this(_skillID : SkillID${
    	  if(flatType.hasDistributedField()) ", __state : api.SkillFile"
    	  else ""
    	}${appendConstructorArguments(t)}) {
    this(_skillID${
    	  if(flatType.hasDistributedField()) ", __state"
    	  else ""
    	})
    ${relevantFields.map{
      case f if f.isDistributed ⇒ s"this.${fieldName(f)} = ${fieldName(f)}"
      case f ⇒ s"${localFieldName(f)} = ${fieldName(f)}"
      }.mkString("\n    ")}
  }
""")
	}

	makeGetterAndSetter(out, t, flatType)
	
	// views
	for(v <- t.getViews.asScala){
	  // just redirect to the actual field so it's way simpler than getters & setters
	  val fieldName = escaped(v.getName.camel)
	  val target = 
	    if(v.getName == v.getTarget.getName) "super." + escaped(v.getTarget.getName.camel())
	    else escaped("_" + v.getTarget.getName.camel())
	    
	  val fieldAssignName = escaped(v.getName.camel + "_=")
	  
	  out.write(s"""
	${comment(v)}${
	  if(v.getName == v.getTarget.getName) "override "
	  else ""
	}def $fieldName : ${mapType(v.getType())} = $target.asInstanceOf[${mapType(v.getType())}]
  ${comment(v)}def $fieldAssignName($fieldName : ${mapType(v.getType())}) : scala.Unit = $target = $fieldName""")
	}

	// usability methods
    out.write(s"""
  override def prettyString : String = s"${name(t)}(#$$skillID${
    (
        for(f <- t.getAllFields.asScala)
          yield if(f.isIgnored) s""", ${f.getName()}: <<ignored>>"""
          else if (!f.isConstant) s""", ${if(f.isAuto)"auto "else""}${f.getName()}: $${${name(f)}}"""
          else s""", const ${f.getName()}: ${f.constantValue()}"""
    ).mkString
  })"

  override def getTypeName : String = "${t.getSkillName}"

  override def toString = "${t.getName.capital}#"+skillID
}
""")

      out.write(s"""
object ${name(t)} {
${ // create unapply method if the type has fields, that can be matched (none or more then 12 is pointless)
  val fs = t.getAllFields.asScala.filterNot(_.isConstant())
  if(0==fs.size || fs.size > 12)""
  else s"""  def unapply(self : ${name(t)}) = ${(for (f ← fs) yield "self."+escaped(f.getName.camel)).mkString("Some(", ", ", ")")}
"""
}
  final class UnknownSubType(
    _skillID : SkillID,
    val owner : Access[_ <: ${name(t)}])
      extends ${name(t)}(_skillID${
        if(flatType.hasDistributedField()) """,
        owner.asInstanceOf[de.ust.skill.common.scala.internal.StoragePool[_, _]].basePool.owner.asInstanceOf[api.SkillFile]
      """
        else ""}) with UnknownObject[${name(t)}] {

    final override def getTypeName : String = owner.name

    final override def prettyString : String = s"$$getTypeName#$$skillID"
  }
}
""");
    }
      
    createInterfaces(out, base)

    out.close()
    }
  }
  
  /**
   * interfaces required to type fields
   * 
   * interfaces created here inherit some type defined in this file, i.e. they have a super class
   */
  private def createInterfaces(out : PrintWriter, base : UserType) {
    for(t <- IRInterfaces if t.getBaseType.getSkillName.equals(base.getSkillName)) {
      out.write(s"""
${
        comment(t)
}sealed trait ${name(t)} extends ${name(t.getSuperType)}${
  (for(s <- t.getSuperInterfaces.asScala)
    yield " with " + name(s)).mkString
} {""")

      makeGetterAndSetter(out, t, null)

      out.write("""
}
""")
    }
  }
  
  /**
   * interfaces required to type fields
   * 
   * interfaces created here inherit no regular type, i.e. they have no super class
   */
  private def createAnnotationInterfaces {
    if(IRInterfaces.forall(_.getBaseType.isInstanceOf[UserType]))
      return;

    val out = files.open(s"TypesOfAnnotation.scala")

    //package
    out.write(s"""package ${this.packageName}

import de.ust.skill.common.scala.SkillID
import de.ust.skill.common.scala.api.SkillObject
import de.ust.skill.common.scala.api.Access
import de.ust.skill.common.scala.api.UnknownObject
${(for(t <- IRInterfaces if !t.getBaseType.isInstanceOf[UserType];
        c <- t.getCustomizations.asScala if c.language.equals("scala");
        is <- c.getOptions.asScala.toMap.get("import").toArray;
        i  <- is.asScala
        ) yield s"import $i;\n").mkString}""")

    for(t <- IRInterfaces if !t.getBaseType.isInstanceOf[UserType]) {
      out.write(s"""
${
        comment(t)
}trait ${name(t)} extends SkillObject${
  (for(s <- t.getSuperInterfaces.asScala)
    yield " with " + name(s)).mkString
} {""")

      makeGetterAndSetter(out, t, null)

      out.write("""
}
""")
    }
      
    out.close
  }
  
  
	///////////////////////
	// getters & setters //
	///////////////////////
  
  private def makeGetterAndSetter(out : PrintWriter, t : Declaration with WithFields, flatType : UserType) {
    val packageName = if(this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.')+1)
    else this.packageName;
    
    for(f <- t.getFields.asScala){
      implicit val thisF = f;
      implicit val thisT = t;

      if(f.isConstant){
        out.write(s"""
  ${comment(f)}final def $fieldName = $makeGetterImplementation
  final private[$packageName] def Internal$localFieldName = $makeGetterImplementation
""")
      } else if(f.isDistributed()) {
        if(t.isInstanceOf[InterfaceType])
        out.write(s"""
  ${comment(f)}def $fieldName : ${mapType(f.getType())};
  ${comment(f)}def $fieldAssignName(${name(f)} : ${mapType(f.getType())}) : scala.Unit;
""")
        else
        out.write(s"""
  ${comment(f)}def $fieldName : ${mapType(f.getType())} = $makeGetterImplementation
  final private[$packageName] def ${escaped("Internal_"+f.getName.camel)} = $makeGetterImplementation
  ${comment(f)}def $fieldAssignName(${name(f)} : ${mapType(f.getType())}) : scala.Unit = $makeSetterImplementation
  final private[$packageName] def ${escaped("Internal_"+f.getName.camel + "_=")}(v : ${mapType(f.getType())}) = $makeGetterImplementation
""")
      } else {
        out.write(s"""
  final protected var $localFieldName : ${mapType(f.getType())} = ${defaultValue(f)}
  ${comment(f)}def $fieldName : ${mapType(f.getType())} = $makeGetterImplementation
  final private[$packageName] def ${escaped("Internal_"+f.getName.camel)} = $localFieldName
  ${comment(f)}def $fieldAssignName(${name(f)} : ${mapType(f.getType())}) : scala.Unit = $makeSetterImplementation
  final private[$packageName] def ${escaped("Internal_"+f.getName.camel + "_=")}(v : ${mapType(f.getType())}) = $localFieldName = v
""")
      }
    }
    
    // create implementations of distributed fields inherited from interfaces
    if(null!=flatType && flatType.hasDistributedField()){
      val _t = t.asInstanceOf[UserType]
      // collect distributed fields that are not projected onto the super type but onto us
      val fields = _t.getAllFields.asScala.filter{
        f ⇒ 
          val name = f.getSkillName
          f.isDistributed() &&
          (null == _t.getSuperType() || !_t.getSuperType.getAllFields.asScala.exists(_==f)) &&
          !_t.getFields.asScala.exists(_.getSkillName.equals(name))
      }
      
      for(f <- fields){
        implicit val thisF = flatType.getFields.asScala.find(_.getSkillName.equals(f.getSkillName)).get;
        implicit val thisT = flatType;
        out.write(s"""
  ${comment(f)}def $fieldName : ${mapType(f.getType())} = $makeGetterImplementation
  final private[$packageName] def ${escaped("Internal_"+f.getName.camel)} = $makeGetterImplementation
  ${comment(f)}def $fieldAssignName(${name(f)} : ${mapType(f.getType())}) : scala.Unit = $makeSetterImplementation
  final private[$packageName] def ${escaped("Internal_"+f.getName.camel + "_=")}(v : ${mapType(f.getType())}) = $makeGetterImplementation
""")
      }
    }
    
    // custom fields
    for(c <- t.getCustomizations.asScala if c.language.equals("scala")){
      val opts = c.getOptions.asScala.toMap
      val mod = opts.get("modifier").map(_.get(0) + " ").getOrElse("")
      val default = opts.get("default").map(_.get(0)).getOrElse("_")
      
      out.write(s"""
  ${comment(c)}${mod}var ${name(c)} : ${c.`type`} = $default; 
""")
    }
  }

  def makeGetterImplementation(implicit t : Declaration, f : Field) : String = {
          if(f.isIgnored){
              s"""throw new IllegalAccessError("${name(f)} has ${if(f.hasIgnoredType)"a type with "else""}an !ignore hint")"""
          } else if(f.isConstant) {
              s"${f.constantValue().toString}.to${mapType(f.getType)}"
          } else if(f.isDistributed()) {
              s"__state.${name(t)}.${knownField(f)}.getR(this)"
          } else { 
              localFieldName
          }
  }

  def makeSetterImplementation(implicit t : Declaration, f : Field) : String = {
          if(f.isIgnored) {
              s"""throw new IllegalAccessError("${name(f)} has ${if(f.hasIgnoredType)"a type with "else""}an !ignore hint")"""
          } else if(f.isDistributed()){
              s"__state.${name(t)}.${knownField(f)}.setR(this, ${name(f)})"
          } else {
              s"{ ${ //@range check
                  if(f.getType().isInstanceOf[GroundType]) {
                      if(f.getType().asInstanceOf[GroundType].isInteger) {
                          f.getRestrictions.asScala.collect{case r:IntRangeRestriction⇒r}.map{r ⇒ s"""require(${r.getLow}L <= ${name(f)} && ${name(f)} <= ${r.getHigh}L, "${name(f)} has to be in range [${r.getLow};${r.getHigh}]"); """}.mkString("")
                      } else if("f32".equals(f.getType.getName)) {
                          f.getRestrictions.asScala.collect{case r:FloatRangeRestriction⇒r}.map{r ⇒ s"""require(${r.getLowFloat}f <= ${name(f)} && ${name(f)} <= ${r.getHighFloat}f, "${name(f)} has to be in range [${r.getLowFloat};${r.getHighFloat}]"); """}.mkString("")
                      } else if("f64".equals(f.getType.getName)) {
                          f.getRestrictions.asScala.collect{case r:FloatRangeRestriction⇒r}.map{r ⇒ s"""require(${r.getLowDouble} <= ${name(f)} && ${name(f)} <= ${r.getHighDouble}, "${name(f)} has to be in range [${r.getLowDouble};${r.getHighDouble}]"); """}.mkString("")
                      } else {
                          ""
                      }
                  } else {
                      ""
                  }
              }${//@monotone modification check
                  if(!t.getRestrictions.asScala.collect{case r:MonotoneRestriction⇒r}.isEmpty) {
                      s"""require(skillID == -1L, "${t.getName} is specified to be monotone and this instance has already been subject to serialization!"); """
                  } else {
                      ""
                  }
              }$localFieldName = ${name(f)} }"
          }
  }
}
