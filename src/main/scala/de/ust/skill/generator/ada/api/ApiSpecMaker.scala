/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.ada.api

import de.ust.skill.generator.ada.GeneralOutputMaker
import de.ust.skill.ir.Declaration
import scala.collection.JavaConversions._

trait APISpecMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val out = files.open(s"""${packagePrefix}-api.ads""")

    out.write(s"""
with Skill.Files;
with Skill.Types.Pools;${
      (
        for (t ← IR)
          yield s"""
with $poolsPackage.${name(t)}_P;"""
      ).mkString
    }

-- parametrization of file, read/write and pool code
package ${PackagePrefix}.Api is

   type File_T is new Skill.Files.File_T with private;
   type File is access File_T;

   -- create a new file using the argument path for I/O
   function Open
     (Path    : String;
      Read_M  : Skill.Files.Read_Mode  := Skill.Files.Read;
      Write_M : Skill.Files.Write_Mode := Skill.Files.Write) return File;

   -- free all memory
   procedure Free (This : access File_T);

   -- write changes to disk, free all memory
   procedure Close (This : access File_T);

   -- user type pools
   -- work around GNAT bug
${
      (for (t ← IR)
        yield s"""
   package ${name(t)}_Pool_P renames ${poolsPackage}.${name(t)}_P;
   subtype ${name(t)}_Pool is ${name(t)}_Pool_P.Pool;
   function ${escapedLonely(name(t)+"s")} (This : not null access constant File_T) return ${name(t)}_Pool;
   pragma Inline (${escapedLonely(name(t)+"s")});
""").mkString
    }
private

   type File_T is new Skill.Files.File_T with record${
      if (IR.isEmpty())
        """
      null;"""
      else
        (for (t ← IR)
          yield s"""
      ${escapedLonely(name(t)+"s")} : ${name(t)}_Pool;"""
        ).mkString
    }
   end record;

end ${PackagePrefix}.Api;
""")

    out.close()
  }
}
