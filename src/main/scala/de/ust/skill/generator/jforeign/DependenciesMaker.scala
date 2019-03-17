/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.jforeign

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * creates copies of required jars in $outPath
 * @author Timm Felden
 */
trait DependenciesMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make

    // safe unnecessary overwrites that cause race conditions on parallel builds anyway
    if (!skipDependencies)
      for (jar ← jars) {
        this.getClass.synchronized({

          val out = new File(depsPath, jar);
          out.getParentFile.mkdirs();

          if (try {
            !out.exists() || sha256(out.getAbsolutePath) != commonJarSum(jar)
          } catch {
            case e : IOException ⇒ false // just continue
          }) {
            Files.deleteIfExists(out.toPath)
            Files.copy(new File("deps/" + jar).toPath, out.toPath)
          }
        })
      }
  }

  val jars = Seq("skill.jvm.common.jar", "skill.jforeign.common.jar")
  lazy val commonJarSum = jars.map { s ⇒ (s -> sha256("deps/" + s)) }.toMap

  final def sha256(name : String) : String = sha256(new File("src/test/resources/" + name).toPath)
  @inline final def sha256(path : Path) : String = {
    val bytes = Files.readAllBytes(path)
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02X".format(_)).mkString
  }
}
