package de.ust.skill.generator.scala.internal.parsers

import java.io.PrintWriter
import de.ust.skill.generator.scala.GeneralOutputMaker

trait FileParserMaker extends GeneralOutputMaker{
  override def make {
    super.make
    val out = open("internal/parsers/FileParser.scala")
    //package & imports
    out.write(s"""package ${packagePrefix}internal.parsers

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

import scala.Array.canBuildFrom
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.language.implicitConversions

import ${packagePrefix}internal._
import ${packagePrefix}internal.pool._
""")

    //the body itself is always the same
    copyFromTemplate(out, "FileParser.scala.template")

    //class prefix
    out.close()
  }
}