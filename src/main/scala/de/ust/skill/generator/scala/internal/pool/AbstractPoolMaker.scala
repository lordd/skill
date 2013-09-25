package de.ust.skill.generator.scala.internal.pool

import java.io.PrintWriter
import de.ust.skill.generator.scala.GeneralOutputMaker

trait AbstractPoolMaker extends GeneralOutputMaker{
  override def make {
    super.make
    val out = open("internal/pool/AbstractPool.scala")
    //package & imports
    out.write(s"""package ${packagePrefix}internal.pool

import scala.collection.mutable.ArrayBuffer

import ${packagePrefix}internal.UserType

/**
 * The super type of all storage pools. This type is necessary in order to have invariant type parameters at storage
 *  pools together with the ability to treat storage pools without knowing anything about the stored type.
 *
 *  @note pool interfaces are called just "XPool" instead of "XStoragePool" in order to avoid name clashes
 *
 *  TODO needs to be abstract, requires generic pools
 */
class AbstractPool(
    val userType: UserType,
    _superPool: ⇒ Option[AbstractPool],
    val blockCount: Int) {
  val name = userType.name
  private[internal] def superPool: Option[AbstractPool] = _superPool

  /**
   * the next pool regarding type order; for example A<:B, B<:D, A<:C may lead to A⇀B⇀D⇀C or A⇀C⇀B⇀D.
   */
  private[internal] var next: AbstractPool = _superPool match {
    case None    ⇒ null
    case Some(p) ⇒ p.next
  }
  // we stole super's next, so we have to set ourselves as next
  _superPool.foreach(_.next = this)

  /**
   * the sub pools are constructed during construction of all storage pools of a state
   */
  protected var subPools = new ArrayBuffer[AbstractPool];
  // update sub-pool relation
  if (superPool.isDefined) {
    superPool.get.subPools += this
  }

  /**
   * the number of instances of exactly this type, excluding sub-types
   */
  def staticSize: Long = 0
  /**
   * the number of instances of this type, including sub-types
   * @note this is an O(t) operation, where t is the number of sub-types
   */
  final def dynamicSize: Long = subPools.map(_.dynamicSize).fold(staticSize)(_ + _)
}""")

    //class prefix
    out.close()
  }
}