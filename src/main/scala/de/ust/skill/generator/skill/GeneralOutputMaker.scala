/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-15 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.skill

import de.ust.skill.ir._
import java.io.File
import java.io.PrintWriter
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import scala.collection.mutable.MutableList
import de.ust.skill.generator.common.Generator
import scala.collection.JavaConversions._
import scala.collection.mutable.HashSet

/**
 * The parent class for all output makers.
 *
 * @author Timm Felden
 */
trait GeneralOutputMaker extends Generator {

  // droppable IR kinds
  sealed abstract class Droppable;
  object Interfaces extends Droppable;
  object Enums extends Droppable;
  object Typedefs extends Droppable;

  // by default, nothing is dropped
  var droppedKinds = HashSet[Droppable]();

  override def getLanguageName = "skill";
  
  /**
   * the result is a single file, hence there is no point to clean anything
   */
  override def clean {}

  // remove special stuff for now
  final def setTC(tc : TypeContext) = this.tc = tc;
  var tc : TypeContext = _

  /**
   * Creates the correct PrintWriter for the argument file.
   */
  override protected def open(path : String) = {
    val f = new File(outPath, path)
    f.getParentFile.mkdirs
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
      new FileOutputStream(f), "UTF-8")))
    // no header required here -> rval.write(header)
    rval
  }

  /**
   * Assume the existence of a translation function for types.
   */
  protected def mapType(t : Type) : String

  /**
   * Assume a package prefix provider.
   */
  protected def packagePrefix : String

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   */
  protected def escaped(target : Name) : String = target.camel

  private lazy val packagePath = if (packagePrefix.length > 0) {
    "/" + packagePrefix.replace(".", "/")
  } else {
    ""
  }
}
