/*  ___ _  ___ _ _                                                                                                    *\
** / __| |/ (_) | |     Your SKilL scala Binding                                                                      **
** \__ \ ' <| | | |__   generated: 01.02.2019                                                                         **
** |___/_|\_\_|_|____|  by: feldentm                                                                                  **
\*                                                                                                                    */
package de.ust.skill.sir.api.internal

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.WrappedArray
import scala.reflect.Manifest

import de.ust.skill.common.jvm.streams.InStream

import de.ust.skill.common.scala.SkillID
import de.ust.skill.common.scala.api.SkillObject
import de.ust.skill.common.scala.api.TypeMissmatchError
import de.ust.skill.common.scala.internal.BasePool
import de.ust.skill.common.scala.internal.FieldDeclaration
import de.ust.skill.common.scala.internal.SkillState
import de.ust.skill.common.scala.internal.SingletonStoragePool
import de.ust.skill.common.scala.internal.StoragePool
import de.ust.skill.common.scala.internal.SubPool
import de.ust.skill.common.scala.internal.fieldTypes._
import de.ust.skill.common.scala.internal.restrictions.FieldRestriction

import _root_.de.ust.skill.sir.api._

final class EnumTypePool(poolIndex : Int,
superPool: UserdefinedTypePool)
    extends SubPool[_root_.de.ust.skill.sir.EnumType, de.ust.skill.sir.Type](
      poolIndex,
      "enumtype",
superPool
    ) {
  override def getInstanceClass: Class[_root_.de.ust.skill.sir.EnumType] = classOf[_root_.de.ust.skill.sir.EnumType]

  override def addField[T : Manifest](ID : Int, t : FieldType[T], name : String,
                           restrictions : HashSet[FieldRestriction]) : FieldDeclaration[T, _root_.de.ust.skill.sir.EnumType] = {
    val f = (name match {
      case "fields" ⇒ new F_EnumType_fields(ID, this, t.asInstanceOf[FieldType[scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.FieldLike]]])
      case "instances" ⇒ new F_EnumType_instances(ID, this, t.asInstanceOf[FieldType[scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Identifier]]])
      case _      ⇒ return super.addField(ID, t, name, restrictions)
    }).asInstanceOf[FieldDeclaration[T, _root_.de.ust.skill.sir.EnumType]]

    //check type
    if (t != f.t)
      throw new TypeMissmatchError(t, f.t.toString, f.name, name)

    val rs = restrictions.iterator
    while(rs.hasNext)
      f.addRestriction(rs.next())

    dataFields += f
    return f
  }
  override def ensureKnownFields(st : SkillState) {
    val state = st.asInstanceOf[SkillFile]
    // data fields
    val Clsfields = classOf[F_EnumType_fields]
    val Clsinstances = classOf[F_EnumType_instances]

    val fields = HashSet[Class[_ <: FieldDeclaration[_, _root_.de.ust.skill.sir.EnumType]]](Clsfields,Clsinstances)
    var dfi = dataFields.size
    while (dfi != 0) {
      dfi -= 1
      fields.remove(dataFields(dfi).getClass)
    }
    if(fields.contains(Clsfields))
        dataFields += new F_EnumType_fields(dataFields.size + 1, this, VariableLengthArray(state.FieldLike))
    if(fields.contains(Clsinstances))
        dataFields += new F_EnumType_instances(dataFields.size + 1, this, VariableLengthArray(state.Identifier))
    // no auto fields


    val fs = (dataFields ++ autoFields).iterator
    while (fs.hasNext)
      fs.next().createKnownRestrictions
  }

  override def makeSubPool(name : String, poolIndex : Int) = new EnumTypeSubPool(poolIndex, name, this)
  override def reflectiveAllocateInstance: _root_.de.ust.skill.sir.EnumType = {
    val r = new _root_.de.ust.skill.sir.EnumType(-1)
    this.newObjects.append(r)
    r
  }

  override def allocateInstances {
    for (b ← blocks.par) {
      var i : SkillID = b.bpo
      val last = i + b.staticCount
      while (i < last) {
        data(i) = new _root_.de.ust.skill.sir.EnumType(i + 1)
        i += 1
      }
    }
  }

  def make(fields : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.FieldLike] = scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.FieldLike](), instances : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Identifier] = scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Identifier](), comment : _root_.de.ust.skill.sir.Comment = null, hints : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Hint] = scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Hint](), restrictions : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Restriction] = scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Restriction](), name : _root_.de.ust.skill.sir.Identifier = null) = {
    val r = new _root_.de.ust.skill.sir.EnumType(-1 - newObjects.size, fields : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.FieldLike], instances : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Identifier], comment : _root_.de.ust.skill.sir.Comment, hints : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Hint], restrictions : scala.collection.mutable.ArrayBuffer[_root_.de.ust.skill.sir.Restriction], name : _root_.de.ust.skill.sir.Identifier)
    newObjects.append(r)
    r
  }
}

final class EnumTypeSubPool(poolIndex : Int, name : String, superPool : StoragePool[_ >: _root_.de.ust.skill.sir.EnumType.UnknownSubType <: _root_.de.ust.skill.sir.EnumType, _root_.de.ust.skill.sir.Type])
    extends SubPool[_root_.de.ust.skill.sir.EnumType.UnknownSubType, _root_.de.ust.skill.sir.Type](
      poolIndex,
      name,
      superPool
    ) {
  override def getInstanceClass : Class[_root_.de.ust.skill.sir.EnumType.UnknownSubType] = classOf[_root_.de.ust.skill.sir.EnumType.UnknownSubType]

  override def makeSubPool(name : String, poolIndex : Int) = new EnumTypeSubPool(poolIndex, name, this)

  override def ensureKnownFields(st : SkillState) {}

  override def allocateInstances {
      for (b ← blocks.par) {
        var i : SkillID = b.bpo
        val last = i + b.staticCount
        while (i < last) {
          data(i) = new _root_.de.ust.skill.sir.EnumType.UnknownSubType(i + 1, this)
          i += 1
        }
      }
    }

    def reflectiveAllocateInstance : _root_.de.ust.skill.sir.EnumType.UnknownSubType = {
      val r = new _root_.de.ust.skill.sir.EnumType.UnknownSubType(-1, this)
      this.newObjects.append(r)
      r
    }
}
