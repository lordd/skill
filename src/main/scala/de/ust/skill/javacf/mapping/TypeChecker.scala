package de.ust.skill.javacf.mapping

import de.ust.skill.ir.Type
import de.ust.skill.ir.TypeContext
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.collection.mutable.Set

class TypeChecker {

  def check(rules: List[TypeRule], from: TypeContext, to: TypeContext) {
    val fromTypes: Set[Type] = collection.mutable.Set() ++ from.getUsertypes.asScala
    val toTypes: Set[Type] = collection.mutable.Set() ++ to.getUsertypes.asScala
    val checked = HashMap.empty[Type, Type]
    
        checked += (from.get("bool") → to.get("bool"))
        checked += (from.get("i8") → to.get("i8"))
        checked += (from.get("i16") → to.get("i16"))
        checked += (from.get("i32") → to.get("i32"))
        checked += (from.get("i64") → to.get("i64"))
        //checked += (from.get("v64") → to.get("v64")) ??
        checked += (from.get("f32") → to.get("f32"))
        checked += (from.get("f64") → to.get("f64"))
        checked += (from.get("string") → to.get("string"))

    rules.foreach {
      _ match {
        case equation: TypeEquation ⇒ {
          val left = equation.getLeft
          val right = equation.getRight

          fromTypes -= left
          toTypes -= right
          if (checked contains left) {
            if (checked(left) != right) {
              println(s"${left} is mapped to ${right} but it was mapped to ${checked(left)} before")
            }
          } else {
            checked += (left → right)
          }
        }
      }
    }
    fromTypes.foreach { unmapped => println(s"warning: SKilL type $unmapped has not been mapped to any Java type") }
    toTypes.foreach { unmapped => println(s"warning: Java type $unmapped is not used by any mapping") }
  }

}