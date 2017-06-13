/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.scala

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

import scala.reflect.io.Path.jfile2path

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import de.ust.skill.generator.common
import de.ust.skill.main.CommandLine
import org.json.JSONTokener
import org.json.JSONObject
import org.json.JSONArray
import scala.collection.JavaConverters._
/**
 * Generic tests built for scala.
 * Generic tests have an implementation for each programming language, because otherwise deleting the generated code
 * upfront would be ugly.
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class JsonTests extends common.GenericJsonTests {

  override def language : String = "scala"

  override def deleteOutDir(out : String) {
    import scala.reflect.io.Directory
    Directory(new File("testsuites/scala/src/main/scala/", out)).deleteRecursively
  }

  override def callMainFor(name : String, source : String, options : Seq[String]) {
    CommandLine.exit = s ⇒ throw new RuntimeException(s)
    CommandLine.main(Array[String](source,
      "--debug-header",
      "-L", "scala",
      "-p", name,
      "-d", "testsuites/scala/lib",
      "-o", "testsuites/scala/src/main/scala") ++ options)
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter = {
    val f = new File(s"testsuites/scala/src/test/scala/$packagePath/Generic${name}Test.scala")
    f.getParentFile.mkdirs
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""
package $packagePath

import java.nio.file.Path

import org.junit.Assert

import de.ust.skill.common.scala.api.Access
import de.ust.skill.common.scala.api.Create
import de.ust.skill.common.scala.api.SkillException
import de.ust.skill.common.scala.api.Read
import de.ust.skill.common.scala.api.ReadOnly
import de.ust.skill.common.scala.api.Write

import $packagePath.api.SkillFile
import de.ust.skill.common.scala.api.SkillObject
import common.CommonTest

/**
 * Tests the file reading capabilities.
 */
class Generic${name}Test extends CommonTest {
  @inline def read(s: String) = SkillFile.open("../../"+s)
""")

    for (path ← collectSkillSpecification(packagePath).getParentFile().listFiles if path.getName.endsWith(".json")) {
      makeTestForJson(rval, path.getAbsolutePath(), packagePath);
    }
    rval
  }

  
  def makeTestForJson(rval: PrintWriter, testfile: String, packagePath : String): PrintWriter = {
    def testname = new File(testfile).getName.replace(".json", "");
    rval.write(s"""
	test("${packagePath} - ${testname}") {
    val path = tmpFile("write.generic");
    val sf = SkillFile.open(path, Create, Write);
    reflectiveInit(sf);
    
    def types = creator.SkillObjectCreator.generateSkillFileTypeMappings(sf);
    def typeFieldMapping = creator.SkillObjectCreator.generateSkillFileFieldMappings(sf);
    
    //auto-generated instansiation from json

"""
      +
      generateObjectInstantiation(testfile)
      +

      """
	}

""")
    rval
  }
  
  
  def generateObjectInstantiation(jsonFile: String): String = {
    val fileTokens = new JSONTokener(new java.io.FileInputStream(jsonFile));
    val content = new JSONObject(fileTokens);
    val jsonObjects = content.getJSONObject("data");
    var instantiations = "";

    if (content.getBoolean("shouldFail")) {
      instantiations = instantiations.concat(
        """			System.out.println("There should be an exception coming up!");
			exception.expect(Exception.class);
      """);
    }

    //First create skillobjects
    for (currentObjKey <- asScalaSetConverter(jsonObjects.keySet()).asScala) { //currentObjKey is the name of the skillobj to create
      val currentObj = jsonObjects.getJSONObject(currentObjKey); //The skillobject to create
      val currentObjType = currentObj.getString("type"); //The type of the skillObject

      instantiations = instantiations.concat("var " + currentObjKey.toLowerCase() + " = types.getOrElse(\"" + currentObjType.toLowerCase() + "\", null);\n");
      instantiations = instantiations.concat(s"""    if(${currentObjKey.toLowerCase()} == null){
      throw new Exception("Unable to find skillObject.");
}
var new${currentObjKey.toLowerCase()}: SkillObject = age.reflectiveAllocateInstance.asInstanceOf[SkillObject];  
""");
    }
    instantiations = instantiations.concat("\n")
    //Set skillobject values
    for (currentObjKey <- asScalaSetConverter(jsonObjects.keySet()).asScala) { //currentObjKey is the name of the skillobj to create
      val currentObj = jsonObjects.getJSONObject(currentObjKey); //The skillobject to create
      val currentObjType = currentObj.getString("type").toLowerCase(); //The type of the skillObject
      val objAttributes = currentObj.getJSONObject("attr"); //The attributes/Fields of the skillObject 
      
      for (currentAttrKey <- asScalaSetConverter(objAttributes.keySet()).asScala) {

        val currentAttrValue = getcurrentAttrValue(objAttributes, currentAttrKey, currentObjKey, currentObjType);

        if (objAttributes.optJSONArray(currentAttrKey) != null) {

          instantiations = instantiateArray(instantiations, objAttributes.getJSONArray(currentAttrKey), currentObjType, currentAttrKey.toLowerCase(), currentAttrValue);

        } else if (objAttributes.optJSONObject(currentAttrKey) != null) {

          instantiations = instantiateMap(instantiations, objAttributes.getJSONObject(currentAttrKey), currentObjType, currentAttrKey.toLowerCase(), currentAttrValue);

        }

        instantiations = instantiations.concat(currentObjKey.toLowerCase() + ".set(cast(typeFieldMapping.get(\"" + currentObjType.toLowerCase() + "\").get(\"" + currentAttrKey.toLowerCase() + "\")), " + currentAttrValue + ");\n\n");

      }
    }
    instantiations = instantiations.concat("sf.close;\n");
    return instantiations;
  }
  
  def closeTestFile(out : java.io.PrintWriter) {
    out.write("""
}
""")
    out.close
  }

  override def makeGenBinaryTests(name : String) {

    val tmp = collectBinaries(name);
    val accept = tmp._1
    val reject = tmp._2

    // generate read tests
    locally {
      val out = newTestFile(name, "Json")

      for (f ← accept) out.write(s"""
  test("$name - read (accept): ${f.getName}") { read("${f.getPath.replaceAll("\\\\", "\\\\\\\\")}").check }
""")

      for (f ← reject) out.write(s"""
  test("$name - read (reject): ${f.getName}") { intercept[SkillException] { read("${f.getPath.replaceAll("\\\\", "\\\\\\\\")}").check } }
""")
      closeTestFile(out)
    }

    // reflective write tests
    locally {
      val out = newTestFile(name, "WriteReflective")

      out.write(s"""
  test("$name - write reflective") {
    val path = tmpFile("write.generic");
    val sf = SkillFile.open(path, Create, Write);
    reflectiveInit(sf);
    sf.close
  }

  test("$name - write reflective checked") {
    val path = tmpFile("write.generic.checked");
    val sf = SkillFile.open(path, Create, Write);
    reflectiveInit(sf);
    // write file
    sf.flush()

    // create a name -> type map
    val types : Map[String, Access[_]] = sf.map(t ⇒ t.name -> t).toMap

    // read file and check skill IDs
    val sf2 = SkillFile.open(path, Read, ReadOnly);
    for (t2 ← sf2) {
      val t = types(t2.name)
      if(t.size != t2.size)
        fail("size missmatch")
    }
  }
""")
      closeTestFile(out)
    }

    //    mit generischem binding sf parsen um an zu erwartende daten zu kommen

    //    mit parser spec parsen um an lesbare daten zu kommen:)

    //    test generieren, der sicherstellt, dass sich die daten da raus lesen lassen

  }

  override def finalizeTests {
    // nothing yet
  }
  
  def instantiateMap(instantiations: String, map: JSONObject, objValueType: String, attrKey: String, mapName: String): String = {
    var ins = instantiations.concat("\n");
    ins = ins.concat("HashMap " + mapName + " = new HashMap<>();\n");
    for (currentObjKey <- asScalaSetConverter(map.keySet()).asScala) {
      var key = currentObjKey;
      if(currentObjKey.contains("\"")){
        key = "wrapPrimitveMapTypes(" + currentObjKey + ", typeFieldMapping.get(\"" + objValueType.toLowerCase() + "\").get(\"" + attrKey.toLowerCase() + "\"),true)";
      }
      var value = map.get(currentObjKey).toString();
      if(map.get(currentObjKey).toString().contains("\"")){
        value = "wrapPrimitveMapTypes(" + map.get(currentObjKey).toString() + ", typeFieldMapping.get(\"" + objValueType.toLowerCase() + "\").get(\"" + attrKey.toLowerCase() + "\"),false)";
      }
      ins = ins.concat(mapName + ".put(" + key + ", " + value + ");\n");
    }
    ins = ins.concat("\n");
    return ins;
  }

  def instantiateArray(instantiations: String, array: JSONArray, objValueType: String, attrKey: String, collectionName: String): String = {
    var ins = instantiations.concat(s"""
    refClass = Class.forName(getProperCollectionType(typeFieldMapping.get("${objValueType}").get("${attrKey}").toString()));
    refConstructor = refClass.getConstructor();
    Collection ${collectionName} = (Collection) refConstructor.newInstance();
    """);
    for (x <- intWrapper(0) until array.length()) {
      ins = ins.concat(collectionName + ".add(" + getcurrentArrayValue(array, x, attrKey, objValueType) + ");\n");
    }
    ins = ins.concat("\n");
    return ins;
  }

  def getcurrentAttrValue(attributes: JSONObject, currentAttrKey: String, currentObjKey: String, currentObjType: String): String = {

    if (attributes.optJSONArray(currentAttrKey) != null) {

      return currentObjKey.toLowerCase() + currentAttrKey + "Collection";

    } else if (attributes.optJSONObject(currentAttrKey) != null) {

      return currentObjKey.toLowerCase() + currentAttrKey.toLowerCase() + "Map";

    } else if (attributes.optBoolean(currentAttrKey) ||
      (!attributes.optBoolean(currentAttrKey) && !attributes.optBoolean(currentAttrKey, true))) {

      return attributes.getBoolean(currentAttrKey).toString();

    } else if (attributes.optDouble(currentAttrKey, 2009) != 2009) {

      return "wrapPrimitveTypes(" + attributes.getDouble(currentAttrKey).toString() + ", typeFieldMapping.get(\"" + currentObjType + "\").get(\"" + currentAttrKey.toLowerCase() + "\"))";

    } else if (attributes.optLong(currentAttrKey, 2009) != 2009) {

      return "wrapPrimitveTypes(" + attributes.getLong(currentAttrKey).toString() + ", typeFieldMapping.get(\"" + currentObjType + "\").get(\"" + currentAttrKey.toLowerCase() + "\"))";

    } else if (!attributes.optString(currentAttrKey).isEmpty()) {
      return attributes.getString(currentAttrKey).toLowerCase();

    } else {

      return "null";
    }
  }
  
    def getcurrentArrayValue(array: JSONArray, currentObj: Int,currentAttrKey : String, currentObjType: String): String = {

    if (array.optBoolean(currentObj) ||
      (!array.optBoolean(currentObj) && !array.optBoolean(currentObj, true))) {

      return array.getBoolean(currentObj).toString();

    } else if (array.optDouble(currentObj, 2009) != 2009) {

      return "wrapPrimitveTypes(" + array.getDouble(currentObj).toString() + ", typeFieldMapping.get(\"" + currentObjType + "\").get(\"" + currentAttrKey.toLowerCase() + "\"))";

    } else if (array.optLong(currentObj, 2009) != 2009) {

      return "wrapPrimitveTypes(" + array.getLong(currentObj).toString() + ", typeFieldMapping.get(\"" + currentObjType + "\").get(\"" + currentAttrKey.toLowerCase() + "\"))";

    } else if (!array.optString(currentObj).isEmpty()) {
      return array.getString(currentObj).toLowerCase();

    } else {

      return "null";
    }
  }
}
