/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.jforeign

import scala.collection.JavaConversions.asScalaBuffer

trait InterfacesMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make

    for (t ← interfaces) {
      val out = files.open(name(t) + ".java")

      //package
      out.write(s"""package ${this.packageName};

import de.ust.skill.common.jforeign.api.FieldDeclaration;
import de.ust.skill.common.jforeign.internal.NamedType;
import de.ust.skill.common.jforeign.internal.SkillObject;
import de.ust.skill.common.jforeign.internal.StoragePool;
""")

      val packageName =
        if (this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.') + 1)
        else this.packageName;

      val fields = t.getAllFields.filter(!_.isConstant)
      val relevantFields = fields.filter(!_.isIgnored)

      out.write(s"""
${
        comment(t)
      }${
        suppressWarnings
      }public interface ${name(t)} ${
        if (t.getSuperInterfaces.isEmpty) ""
        else
          t.getSuperInterfaces.map(name(_)).mkString("extends ", ", ", "")
      } {

    /**
     * cast to concrete type
     */${
        if (!t.getSuperInterfaces.isEmpty()) """
    @Override"""
        else ""
      }
    public default ${mapType(t.getSuperType)} self() {
        return (${mapType(t.getSuperType)}) this;
    }
${
        ///////////////////////
        // getters & setters //
        ///////////////////////
        (
          for (f ← t.getAllFields) yield {
            if (f.isConstant)
              s"""
    //TODO default? ${comment(f)}static public ${mapType(f.getType())} get${escaped(f.getName.capital)}();
"""
            else
              s"""
    ${comment(f)}public ${mapType(f.getType())} get${escaped(f.getName.capital)}();

    ${comment(f)}public void set${escaped(f.getName.capital)}(${mapType(f.getType())} ${name(f)});
"""
          }
        ).mkString
      }
}
""");
      out.close()
    }
  }
}
