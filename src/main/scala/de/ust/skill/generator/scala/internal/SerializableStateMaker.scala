package de.ust.skill.generator.scala.internal

import java.io.PrintWriter
import scala.collection.JavaConversions.asScalaBuffer
import de.ust.skill.ir.Declaration
import de.ust.skill.generator.scala.GeneralOutputMaker
trait SerializableStateMaker extends GeneralOutputMaker{
  override def make {
    super.make
    val out = open("internal/SerializableState.scala") 
      
    //package & imports
    out.write(s"""package ${packagePrefix}internal

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import ${packagePrefix}internal.pool._
import ${packagePrefix}internal.types._
""")

    // first part: exceptions, internal structure, file writing
    copyFromTemplate(out, "SerializableState.scala.part1.template")

    //access to declared types
    IR.foreach({ t ⇒
      val name = t.getName()
      val Name = name.capitalize
      val sName = name.toLowerCase()
      val tName = packagePrefix + name

      // TODO t.fields.fold...
      val addArgs = "date:Long"
      val consArgs = "date"

      out.write(s"""
  /**
   * returns a $name iterator
   */
  def get$Name(): Iterator[$tName] = new Iterator[$tName](pools("$sName"))

  /**
   * adds a new $name to the $name pool
   */
  def add$Name($addArgs) = pools("$sName").asInstanceOf[${Name}StoragePool].add$Name(new $tName($consArgs))

""")

    })

    // second part: debug stuff; reading of files
    copyFromTemplate(out, "SerializableState.scala.part2.template")

    out.close()
  }
}