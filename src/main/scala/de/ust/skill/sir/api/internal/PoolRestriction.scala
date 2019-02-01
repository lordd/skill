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

final class RestrictionPool(poolIndex : Int)
    extends BasePool[_root_.de.ust.skill.sir.Restriction](
      poolIndex,
      "restriction"
    ) {
  override def getInstanceClass: Class[_root_.de.ust.skill.sir.Restriction] = classOf[_root_.de.ust.skill.sir.Restriction]

  override def addField[T : Manifest](ID : Int, t : FieldType[T], name : String,
                           restrictions : HashSet[FieldRestriction]) : FieldDeclaration[T, _root_.de.ust.skill.sir.Restriction] = {
    val f = (name match {
      case "arguments" ⇒ new F_Restriction_arguments(ID, this, t.asInstanceOf[FieldType[scala.collection.mutable.ArrayBuffer[java.lang.String]]])
      case "name" ⇒ new F_Restriction_name(ID, this, t.asInstanceOf[FieldType[java.lang.String]])
      case _      ⇒ return super.addField(ID, t, name, restrictions)
    }).asInstanceOf[FieldDeclaration[T, _root_.de.ust.skill.sir.Restriction]]

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
    val Clsarguments = classOf[F_Restriction_arguments]
    val Clsname = classOf[F_Restriction_name]

    val fields = HashSet[Class[_ <: FieldDeclaration[_, _root_.de.ust.skill.sir.Restriction]]](Clsarguments,Clsname)
    var dfi = dataFields.size
    while (dfi != 0) {
      dfi -= 1
      fields.remove(dataFields(dfi).getClass)
    }
    if(fields.contains(Clsarguments))
        dataFields += new F_Restriction_arguments(dataFields.size + 1, this, VariableLengthArray(state.String))
    if(fields.contains(Clsname))
        dataFields += new F_Restriction_name(dataFields.size + 1, this, state.String)
    // no auto fields


    val fs = (dataFields ++ autoFields).iterator
    while (fs.hasNext)
      fs.next().createKnownRestrictions
  }

  override def makeSubPool(name : String, poolIndex : Int) = new RestrictionSubPool(poolIndex, name, this)

  override def allocateData : Unit = data = new Array[_root_.de.ust.skill.sir.Restriction](cachedSize)
  override def reflectiveAllocateInstance: _root_.de.ust.skill.sir.Restriction = {
    val r = new _root_.de.ust.skill.sir.Restriction(-1)
    this.newObjects.append(r)
    r
  }

  override def allocateInstances {
    for (b ← blocks.par) {
      var i : SkillID = b.bpo
      val last = i + b.staticCount
      while (i < last) {
        data(i) = new _root_.de.ust.skill.sir.Restriction(i + 1)
        i += 1
      }
    }
  }

  def make(arguments : scala.collection.mutable.ArrayBuffer[java.lang.String] = scala.collection.mutable.ArrayBuffer[java.lang.String](), name : java.lang.String = null) = {
    val r = new _root_.de.ust.skill.sir.Restriction(-1 - newObjects.size, arguments : scala.collection.mutable.ArrayBuffer[java.lang.String], name : java.lang.String)
    newObjects.append(r)
    r
  }
}

final class RestrictionSubPool(poolIndex : Int, name : String, superPool : StoragePool[_ >: _root_.de.ust.skill.sir.Restriction.UnknownSubType <: _root_.de.ust.skill.sir.Restriction, _root_.de.ust.skill.sir.Restriction])
    extends SubPool[_root_.de.ust.skill.sir.Restriction.UnknownSubType, _root_.de.ust.skill.sir.Restriction](
      poolIndex,
      name,
      superPool
    ) {
  override def getInstanceClass : Class[_root_.de.ust.skill.sir.Restriction.UnknownSubType] = classOf[_root_.de.ust.skill.sir.Restriction.UnknownSubType]

  override def makeSubPool(name : String, poolIndex : Int) = new RestrictionSubPool(poolIndex, name, this)

  override def ensureKnownFields(st : SkillState) {}

  override def allocateInstances {
      for (b ← blocks.par) {
        var i : SkillID = b.bpo
        val last = i + b.staticCount
        while (i < last) {
          data(i) = new _root_.de.ust.skill.sir.Restriction.UnknownSubType(i + 1, this)
          i += 1
        }
      }
    }

    def reflectiveAllocateInstance : _root_.de.ust.skill.sir.Restriction.UnknownSubType = {
      val r = new _root_.de.ust.skill.sir.Restriction.UnknownSubType(-1, this)
      this.newObjects.append(r)
      r
    }
}
