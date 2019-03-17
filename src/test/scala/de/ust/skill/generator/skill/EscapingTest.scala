/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.skill

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import de.ust.skill.generator.common.KnownGenerators
import de.ust.skill.main.CommandLine

/**
 * Java specific tests.
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class EscapingTest extends FunSuite {

  def check(language : String, words : Array[String], escaping : Array[Boolean]) {
    CommandLine.exit = { s ⇒ fail(s) }
    val result = CommandLine.checkEscaping(language, words)

    assert(result === escaping.mkString(" "))
  }

  val known = KnownGenerators.all.map(_.newInstance.getLanguageName)

  // if is a keyword in all real languages
  for (l ← known if !Set[String]("sidl", "skill", "statistics", "ecore").contains(l))
    test(s"${l} - none")(check(l, Array("if"), Array(true)))

  // some language keywords
  test("Ada - keywords") {
    check("ada", Array("while", "others", "in", "out", "case"), Array(true, true, true, true, true))
  }

  test("Java - keywords") {
    check("java", Array("int", "is", "not", "a", "class"), Array(true, false, false, false, true))
  }
}
