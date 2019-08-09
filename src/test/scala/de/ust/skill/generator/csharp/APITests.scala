/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.csharp

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaIterator
import scala.io.Source

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import de.ust.skill.generator.common
import de.ust.skill.ir.Field
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.SetType
import de.ust.skill.ir.SingleBaseTypeContainer
import de.ust.skill.ir.Type
import de.ust.skill.ir.TypeContext
import de.ust.skill.main.CommandLine

/**
  * Generic API tests built for C#.
  *
  * @author Simon Glaub, Timm Felden
  */
@RunWith(classOf[JUnitRunner])
class APITests extends common.GenericAPITests {

  override val language = "csharp"

  val generator = new Main
  import generator._

  override def deleteOutDir(out : String) {
  }

  override def callMainFor(name : String, source : String, options : Seq[String]) {
    CommandLine.main(Array[String](
      source,
      "--debug-header",
      "-c",
      "-L", "csharp",
      "-p", name,
      "-d", "testsuites/csharp/lib",
      "-o", "testsuites/csharp/src/main/csharp/") ++ options)
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter = {
    val f = new File(s"testsuites/csharp/src/test/csharp/$packagePath/Generic${name}Test.cs")
    f.getParentFile.mkdirs
    if (f.exists)
      f.delete
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""using System.Collections.Generic;
using System.Linq;
using System.IO;

using NUnit.Framework;

using SkillFile = $packagePath.api.SkillFile;
using SkillException= de.ust.skill.common.csharp.api.SkillException;
using Mode = de.ust.skill.common.csharp.api.Mode;

namespace $packagePath
{

    /// <summary>
    /// Tests the file reading capabilities.
    /// </summary>
    [TestFixture]
    public class Generic${name}Test : common.CommonTest {
""")
    rval
  }

  def closeTestFile(out : java.io.PrintWriter) {
    out.write("""
    }
}
""")
    out.close
  }

  def makeSkipTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean) {
    out.write(s"""
        [Test]
        public void APITest_${escaped(kind)}_${name}_skipped_${escaped(testName)}() {${
          if (accept) ""
          else """
             Assert.Fail("The test was skipped by the test generator.");"""
        }
        }
""")
  }

  def makeRegularTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean, tc : TypeContext, obj : JSONObject) {
    out.write(s"""
        [Test]
        public void APITest_${escaped(kind)}_${name}_${if (accept) "acc" else "fail"}_${escaped(testName)}() {
            string path = tmpFile("$testName");
            ${if (accept) s"""SkillFile sf = SkillFile.open(path, Mode.Create, Mode.Write);

            // create objects${createObjects(obj, tc, name)}
            // set fields${setFields(obj, tc)}
            sf.close();

            { // read back and assert correctness
                SkillFile sf2 = SkillFile.open(sf.currentPath(), Mode.Read, Mode.ReadOnly);
                // check count per Type${createCountChecks(obj, tc, name)}
                // create objects from file${createObjects2(obj, tc, name)}
                // assert fields${assertFields(obj, tc)}
            }"""
            else s"""try
            {
                SkillFile sf = SkillFile.open(path, Mode.Create, Mode.Write);

                // create objects${createObjects(obj, tc, name)}
                // set fields${setFields(obj, tc)}
                sf.close();

                { // read back and assert correctness
                    SkillFile sf2 = SkillFile.open(sf.currentPath(), Mode.Read, Mode.ReadOnly);
                    // check count per Type${createCountChecks(obj, tc, name)}
                    // create objects from file${createObjects2(obj, tc, name)}
                    // assert fields${assertFields(obj, tc)}
                }
            }
            catch (SkillException)
            {
                return;
            }"""}
            File.Delete(path);
        }
""")
  }


  private def typ(tc : TypeContext, name : String) : String = {
    val n = name.toLowerCase()
    try {
      escaped((tc.getUsertypes ++ tc.getInterfaces).filter(_.getSkillName.equals(n)).head.getName.capital)
    } catch {
      case e : NoSuchElementException ⇒ fail(s"Type '$n' does not exist, fix your test description!")
    }
  }

  private def field(tc : TypeContext, typ : String, field : String) = {
    val tn = typ.toLowerCase()
    val t = tc.getUsertypes.find(_.getSkillName.equals(tn)).get
    val fn = field.toLowerCase()

    try {
      t.getAllFields.find(_.getSkillName.equals(fn)).get
    } catch {
      case e : NoSuchElementException ⇒ fail(s"Field '$fn' does not exist, fix your test description!")
    }
  }

  private def equalValue(left : String, v : Any, f : Field) : String = equalValue(left, v, f.getType)

  private def equalValue(left : String, v : Any, t : Type) : String = t match {
    case t : GroundType ⇒
      t.getSkillName match {
        case "string" if null != v ⇒ s"""$left != null && $left.Equals("${v.toString()}")"""
        case "i8" ⇒ s"$left == (sbyte)" + v.toString()
        case "i16" ⇒ s"$left == (short)" + v.toString()
        case "f32" ⇒ s"$left == (float)" + v.toString()
        case "f64" ⇒ s"$left == (double)" + v.toString()
        case "v32" | "i32" ⇒ s"$left == " + v.toString()
        case "v64" | "i64" ⇒ s"$left == " + v.toString() + "L"
        case _ if null != v && !v.toString().equals("null") && !v.toString().equals("true") && !v.toString().equals("false") ⇒ s"$left == " + v.toString() + "_2"
        case _ ⇒ s"$left == " + v.toString()
      }

    case t : SingleBaseTypeContainer ⇒
      v.asInstanceOf[JSONArray].iterator().toArray.map(value(_, t.getBaseType, "_2")).mkString(t match {
        case t : ListType ⇒ s"$left != null && Enumerable.SequenceEqual($left, list<${mapType(t.getBaseType())}>("
        case t : SetType  ⇒ s"$left != null && Enumerable.SequenceEqual($left, set<${mapType(t.getBaseType())}>("
        case _            ⇒ s"$left != null && ArrayListEqual($left, array<${mapType(t.getBaseType())}>("
      }, ", ", " ))").replace("java.util.", "")

    case t : MapType if v != null ⇒ s"$left != null && Enumerable.SequenceEqual($left, " + valueMap(v.asInstanceOf[JSONObject], t.getBaseTypes.toList, "_2") + ")"

    case _ ⇒
      if (v == null || v.toString().equals("null")) s"$left == (${mapType(t)}) null"
      else s"$left == ${v.toString()}_2"
  }

  //private def value(v : Any, f : Field, suffix : String = "") : String = value(v, f.getType, suffix)

  private def value(v : Any, t : Type, suffix : String = "") : String = t match {
    case t : GroundType ⇒
      t.getSkillName match {
        case "string" if null != v ⇒ s""""${v.toString()}""""
        case "i8"                  ⇒ "(sbyte)" + v.toString()
        case "i16"                 ⇒ "(short)" + v.toString()
        case "f32"                 ⇒ "(float)" + v.toString()
        case "f64"                 ⇒ "(double)" + v.toString()
        case "v64" | "i64"         ⇒ v.toString() + "L"
        case _ if null != v        ⇒ v.toString() + suffix
        case _                     ⇒ v.toString()
      }

    case t : SingleBaseTypeContainer ⇒
      v.asInstanceOf[JSONArray].iterator().toArray.map(value(_, t.getBaseType, suffix)).mkString(t match {
        case t : ListType ⇒ s"list<${mapType(t.getBaseType())}>("
        case t : SetType  ⇒ s"set<${mapType(t.getBaseType())}>("
        case _            ⇒ s"array<${mapType(t.getBaseType())}>("
      }, ", ", ")").replace("java.util.", "")

    case t : MapType if v != null ⇒ valueMap(v.asInstanceOf[JSONObject], t.getBaseTypes.toList, suffix)

    case _ ⇒
      if (v == null || v.toString().equals("null")) s"(${mapType(t)}) null"
      else v.toString() + suffix
  }

  private def valueMap(v : Any, ts : List[Type], suffix : String = "") : String = {
    if (1 == ts.length) {
      value(v, ts.head, suffix)
    } else if (2 == ts.length) {
      var rval = s"map<${mapType(ts.head, false)}, ${mapType(ts(1), false)} >()"
      val obj = v.asInstanceOf[JSONObject]

      // https://docs.scala-lang.org/overviews/collections/maps.html#operations-in-class-map
      // ms put (k, v) Adds mapping from key k to value v to ms and returns any value previously associated with k as an option.
      for (name ← JSONObject.getNames(obj)) {
        rval = s"put($rval, ${value(name, ts.head, suffix)}, ${valueMap(obj.get(name), ts.tail, suffix)})"
      }

      rval;
    } else {
      var rval = s"map<${mapType(ts.head, false)}, Dictionary< ${mapType(ts(1), false)}, ${mapType(ts(2), false)} > >()"
      val obj = v.asInstanceOf[JSONObject]

      // https://docs.scala-lang.org/overviews/collections/maps.html#operations-in-class-map
      // ms put (k, v) Adds mapping from key k to value v to ms and returns any value previously associated with k as an option.
      for (name ← JSONObject.getNames(obj)) {
        rval = s"put($rval, ${value(name, ts.head, suffix)}, ${valueMap(obj.get(name), ts.tail, suffix)})"
      }

      rval;
    }
  }

  private def createCountChecks(obj : JSONObject, tc : TypeContext, packagePath : String) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val objCountPerType = scala.collection.mutable.Map[String, Int]()
      for (name ← JSONObject.getNames(obj)) {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);
        if (!(objCountPerType contains typeName)) {
          objCountPerType += (typeName -> 0)
        }
        objCountPerType(typeName) = objCountPerType(typeName) + 1
      }

      val rval = for ((typeName, objCount) ← objCountPerType) yield {
        s"""
                    Assert.AreEqual(${objCount}, sf.${typeName}s().staticSize());"""
      }

      rval.mkString
    }
  }

  private def createObjects2(obj : JSONObject, tc : TypeContext, packagePath : String) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);

        s"""
                    $packagePath.$typeName ${name}_2 = ($packagePath.$typeName)sf2.${typeName}s().getByID($name.SkillID);"""
      }

      rval.mkString
    }
  }

  private def createObjects(obj : JSONObject, tc : TypeContext, packagePath : String) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);

        s"""
                $packagePath.${typeName} $name = ($packagePath.$typeName)sf.${typeName}s().make();"""
      }

      rval.mkString
    }
  }

  private def assertFields(obj : JSONObject, tc : TypeContext) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;
        val fs = x.getJSONObject(t);

        if (null == JSONObject.getNames(fs))
          ""
        else {
          val assignments = for (fieldName ← JSONObject.getNames(fs).toSeq) yield {
            val f = field(tc, t, fieldName)
            val getter = escaped(f.getName.camel)

            // do not check auto fields as they cannot obtain the stored value from file
            if (f.isAuto()) ""
            else s"""
                    Assert.IsTrue(${equalValue(s"${name}_2.$getter", fs.get(fieldName), f)});"""
          }

          assignments.mkString
        }
      }

      rval.mkString("\n")
    }
  }

  private def setFields(obj : JSONObject, tc : TypeContext) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;
        val fs = x.getJSONObject(t);

        if (null == JSONObject.getNames(fs))
          ""
        else {
          val assignments = for (fieldName ← JSONObject.getNames(fs).toSeq) yield {
            val f = field(tc, t, fieldName)
            val setter = escaped(f.getName.camel)

            s"""
            $name.$setter = (${mapType(f.getType(), false)})${value(fs.get(fieldName), f.getType)};"""
          }

          assignments.mkString
        }
      }

      rval.mkString("\n")
    }
  }
}