/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.statistics

import scala.annotation.tailrec
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.bufferAsJavaList

import de.ust.skill.ir.ContainerType
import de.ust.skill.ir.UserType
import de.ust.skill.ir.restriction.AbstractRestriction
/**
 * Creates user type equivalents.
 *
 * @author Timm Felden
 */
trait CSVMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val ts = tc.getTypedefs
    if (!ts.isEmpty) {

      // types
      locally {
        val out = files.open(s"""types.csv""")
        out.write("type;count\n")
        tc.removeSpecialDeclarations().getUsertypes.flatMap(_.getFields).map(_.getType).groupBy(_.getSkillName).map {
          case (s, ts) ⇒ (s, ts.size)
        }.foreach {
          case (t, count) ⇒ out.write(s"$t;$count\n")
        }
        out.close()
      }

      // type categories
      locally {
        val out = files.open(s"""types category.csv""")
        out.write("type category;count\n")
        tc.removeSpecialDeclarations().getUsertypes.flatMap(_.getFields).map(_.getType).groupBy {
          case t : UserType      ⇒ "ref"
          case t : ContainerType ⇒ t.getClass.getSimpleName
          case t                 ⇒ t.getSkillName
        }.map {
          case (s, ts) ⇒ (s, ts.size)
        }.foreach {
          case (t, count) ⇒ out.write(s"$t;$count\n")
        }
        out.close()
      }

      // field counts
      locally {
        val out = files.open(s"""field counts.csv""")
        out.write("type;fields;total\n")
        tc.removeSpecialDeclarations().getUsertypes.foreach {
          case t ⇒ out.write(s"$t;${t.getFields.size};${t.getAllFields.size}\n")
        }
        out.close()
      }

      // type fancyness
      locally {
        val out = files.open(s"""fancy types.csv""")
        out.write("type category;count\n")
        out.write(s"interface;${tc.getInterfaces.size()}\n")
        out.write(s"enum;${tc.getEnums.size()}\n")
        out.write(s"user;${tc.getUsertypes.size()}\n")
        out.write(s"..which are abstract;${tc.getUsertypes.filter(_.getRestrictions.exists(_.isInstanceOf[AbstractRestriction])).size()}\n")
        out.write(s"typedefs;${tc.getTypedefs.size()}\n")
        out.close()
      }

      // type hierarchy height
      locally {
        @tailrec @inline def depth(t : UserType, d : Int = 0) : Int = t.getSuperType match {
          case null ⇒ d
          case s    ⇒ depth(s, d + 1)
        }

        val out = files.open(s"""types hierarchy.csv""")
        out.write("super type count;count\n")
        tc.removeSpecialDeclarations().getUsertypes.map(depth(_)).groupBy { x ⇒ x }.map {
          case (s, ts) ⇒ (s, ts.size)
        }.foreach {
          case (t, count) ⇒ out.write(s"$t;$count\n")
        }
        out.close()
      }
    }
  }
}
