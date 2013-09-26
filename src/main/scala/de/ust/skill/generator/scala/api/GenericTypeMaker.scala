package de.ust.skill.generator.scala.api

import de.ust.skill.generator.scala.GeneralOutputMaker

trait GenericTypeMaker extends GeneralOutputMaker {
  override def make {
    super.make
    val out = open("api/GenericType.scala")
    //package & imports
    out.write(s"""package ${packagePrefix}api

/**
 * Any unknown object is instantiated by this type.
 *
 * @note not yet implemented; this will get eventually provide setters getters and a way to retrieve type info for fields
 *
 * @author Timm Felden
 */
class GenericType extends SkillType {

  def prettyString: String = "<<some generic objcet>>"

}
""")

    out.close()
  }
}