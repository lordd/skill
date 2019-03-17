/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.doxygen

import scala.collection.JavaConversions.asScalaBuffer
/**
 * Creates user type equivalents.
 *
 * @author Timm Felden
 */
trait TypedefMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val ts = tc.getTypedefs

    if (!ts.isEmpty) {
      val out = files.open(s"""src/_typedefs.h""")

      out.write(s"""
// typedefs inside of the project
#include <string>
#include <list>
#include <set>
#include <map>
#include <stdint.h>

${
        (for (t ← ts)
          yield s"""
${
          comment(t)
        }typedef ${mapType(t.getTarget)} ${t.getName.capital};
""").mkString
      }
""")

      out.close()
    }
  }
}
