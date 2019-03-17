/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.cpp

import scala.collection.JavaConversions.asScalaBuffer

import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.Field
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.InterfaceType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.Restriction
import de.ust.skill.ir.SetType
import de.ust.skill.ir.Type
import de.ust.skill.ir.UserType
import de.ust.skill.ir.VariableLengthArrayType
import de.ust.skill.ir.restriction.ConstantLengthPointerRestriction
import de.ust.skill.ir.restriction.DefaultRestriction
import de.ust.skill.ir.restriction.FloatDefaultRestriction
import de.ust.skill.ir.restriction.FloatRangeRestriction
import de.ust.skill.ir.restriction.IntDefaultRestriction
import de.ust.skill.ir.restriction.IntRangeRestriction
import de.ust.skill.ir.restriction.NameDefaultRestriction
import de.ust.skill.ir.restriction.NonNullRestriction
import de.ust.skill.ir.restriction.CodingRestriction

trait FieldDeclarationsMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make

    makeHeader
    makeSource
  }
  private def makeHeader {

    // one file per base type
    for (base ← IR if null == base.getSuperType) {
      val out = files.open(s"${name(base)}FieldDeclarations.h")

      out.write(s"""${beginGuard(s"${name(base)}_field_declarations")}
#include <skill/fieldTypes/AnnotationType.h>
#include <skill/api/SkillFile.h>
#include "${storagePool(base)}s.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace internal {
""")

      out.write((for (t ← IR if base == t.getBaseType; f ← t.getFields) yield s"""
        /**
         * ${f.getType.toString} ${t.getName.capital}.${f.getName.camel}
         */
        class ${knownField(f)} : public ::skill::internal::FieldDeclaration {
        public:
            ${knownField(f)}(
                    const ::skill::FieldType *const type, const ::skill::string_t *name, ::skill::TypeID index,
                    ::skill::internal::AbstractStoragePool *const owner);

            virtual bool check() const;

            virtual void rsc(::skill::SKilLID i, const ::skill::SKilLID h, ::skill::streams::MappedInStream *in) override;

            virtual ::skill::api::Box getR(const ::skill::api::Object *i) {
                ${
        if (f.isConstant()) s"return ::skill::api::box((${mapType(f.getType)})${f.constantValue()}L);"
        else s"return ::skill::api::box(((${mapType(t)})i)->${internalName(f)});"
      }
            }

            virtual void setR(::skill::api::Object *i, ::skill::api::Box v) {${
        if (f.isConstant()) ""
        else s"""
                ((${mapType(t)})i)->${internalName(f)} = (${mapType(f.getType)})v.${unbox(f.getType)};"""
      }}

            virtual size_t osc() const;

            virtual void wsc(::skill::streams::MappedOutStream* out) const;
        };""").mkString)

      out.write(s"""
    }
${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")

      out.close()
    }
  }

  private def makeSource {

    // one file per base type
    for (base ← IR if null == base.getSuperType) {
      val out = files.open(s"${name(base)}FieldDeclarations.cpp")

      out.write(s"""
#include "${name(base)}FieldDeclarations.h"${
        (for (t ← IR if null == t.getSuperType) yield s"""
#include "${storagePool(t)}s.h"""").mkString
      }
${
        (for (t ← IR if base == t.getBaseType; f ← t.getFields) yield {
          val tIsBaseType = t.getSuperType == null

          val fieldName = s"$packageName::internal::${knownField(f)}"
          val accessI = s"d[i]->${internalName(f)}"
          val readI = s"$accessI = ${readType(f.getType, accessI)};"
          s"""
$fieldName::${knownField(f)}(
        const ::skill::FieldType *const type,
        const ::skill::string_t *name, ::skill::TypeID index,
        ::skill::internal::AbstractStoragePool *const owner)
        : FieldDeclaration(type, name, index, owner) {
${
            (for (r ← f.getRestrictions if !r.isInstanceOf[CodingRestriction]) yield s"""
    addRestriction(${makeRestriction(f.getType, r)});""").mkString
          }
}

void $fieldName::rsc(::skill::SKilLID i, const ::skill::SKilLID h, ::skill::streams::MappedInStream *in) {${
            if (f.isConstant()) "\n    // reading constants is O(0)"
            else s"""
    auto d = ((${storagePool(t)} *) owner)->data;
    for (; i != h; i++) {
        $readI
    }"""
          }
}

std::size_t ($fieldName::osc)() const {${
            if (f.isConstant())
              """
    return 0; // this field is constant"""
            else {
              def fastOffset(fieldType : Type) : Boolean = fieldType match {
                case fieldType : GroundType ⇒ fieldType.getSkillName match {
                  case "annotation" | "string" | "v64" ⇒ false
                  case _                               ⇒ true
                }
                case _ ⇒ false
              }
              def offsetCode(fieldType : Type) : String = fieldType match {

                // read next element
                case fieldType : GroundType ⇒ fieldType.getSkillName match {

                  case "annotation" ⇒ s"""result += type->offset(::skill::box($accessI));"""

                  case "string" ⇒ s"""
            auto t = (::skill::internal::StringPool*) type;
            auto v = $accessI;
            if(nullptr==v)
                result++;
            else
                result += t->offset(v);"""

                  case "i8" | "bool" ⇒ s"""
        return target->count;"""

                  case "i16" ⇒ s"""
        return 2 * target->count;"""

                  case "i32" | "f32" ⇒ s"""
        return 4 * target->count;"""

                  case "i64" | "f64" ⇒ s"""
        return 8 * target->count;"""

                  case "v64" ⇒ s"""const uint64_t v = ::skill::unsign($accessI);

            if (v < 0x80U) {
                result += 1;
            } else if (v < 0x4000U) {
                result += 2;
            } else if (v < 0x200000U) {
                result += 3;
            } else if (v < 0x10000000U) {
                result += 4;
            } else if (v < 0x800000000U) {
                result += 5;
            } else if (v < 0x40000000000U) {
                result += 6;
            } else if (v < 0x2000000000000U) {
                result += 7;
            } else if (v < 0x100000000000000U) {
                result += 8;
            } else {
                result += 9;
            }"""
                  case _ ⇒ s"""
        throw new NoSuchMethodError();"""
                }

                case fieldType : UserType ⇒ s"""${mapType(f.getType)} instance = $accessI;
            if (nullptr == instance) {
                result += 1;
            } else {
            const uint64_t v = ::skill::unsign(skillID(instance));

            if (v < 0x80U) {
                result += 1;
            } else if (v < 0x4000U) {
                result += 2;
            } else if (v < 0x200000U) {
                result += 3;
            } else if (v < 0x10000000U) {
                result += 4;
            } else if (v < 0x800000000U) {
                result += 5;
            } else if (v < 0x40000000000U) {
                result += 6;
            } else if (v < 0x2000000000000U) {
                result += 7;
            } else if (v < 0x100000000000000U) {
                result += 8;
            } else {
                result += 9;
            }}"""

                case fieldType : InterfaceType ⇒ offsetCode(fieldType.getSuperType)

                //                case t : ConstantLengthArrayType ⇒ s"((skill::fieldTypes::ConstantLengthArray*)type)->offset($accessI)"
                //                case t : VariableLengthArrayType ⇒ s"((skill::fieldTypes::VariableLengthArray*)type)->offset(::skill::box($accessI))"
                case t : ListType              ⇒ s"result += ((skill::fieldTypes::ListType*)type)->offset<${mapType(t.getBaseType)}>($accessI);"
                //                case t : SetType                 ⇒ s"((skill::fieldTypes::SetType*)type)->offset(::skill::box($accessI))"

                case _                         ⇒ s"""result += type->offset(::skill::box($accessI));"""
              }

              if (fastOffset(f.getType)) {
                s"""
    const ::skill::internal::Chunk *target = dataChunks.back();
    ${offsetCode(f.getType)}"""

              } else {
                s"""
    ${mapType(t)}* d = ((${storagePool(t)}*) owner)->data;
    const auto target = dynamic_cast<const ::skill::internal::SimpleChunk *>(dataChunks.back());
    std::size_t result = 0L;
    ::skill::SKilLID i = 1 + target->bpo;
    const ::skill::SKilLID high = i + target->count;
    for (; i != high; ++i) {
        ${offsetCode(f.getType)}
    }
    return result;"""
              }
            }
          }
}

void $fieldName::wsc(::skill::streams::MappedOutStream *out) const {${
            if (f.isConstant()) """
    // -done-"""
            else s"""
    ${mapType(t)}* d = ((${storagePool(t)}*) owner)->data;
    const auto target = dynamic_cast<const ::skill::internal::SimpleChunk *>(dataChunks.back());
    ::skill::SKilLID i = 1 + target->bpo;
    const ::skill::SKilLID high = i + target->count;
    for (; i != high; i++) {
        ${writeCode(accessI, f)}
    }"""
          }
}

bool $fieldName::check() const {
${
            val checks = (for (r ← f.getRestrictions)
              yield checkRestriction(f.getType, r)).mkString;
            if (f.isConstant || checks.isEmpty()) "    // nothing to check"
            else s"""
    ${storagePool(t)} *p = (${storagePool(t)} *) owner;
    for (const auto& i : *p) {
        const auto v = i.${internalName(f)};
$checks
    }
"""
          }
    return true;
}
"""
        }).mkString
      }""")

      out.close()
    }
  }

  /**
   * choose a good parse expression
   *
   * @note accessI is only used to create inner maps correctly
   */
  private final def readType(t : Type, accessI : String, typ : String = "type") : String = t match {
    case t : GroundType ⇒ t.getSkillName match {
      case "annotation" ⇒ s"$typ->read(*in).annotation"
      case "string"     ⇒ s"$typ->read(*in).string"
      case "bool"       ⇒ "in->boolean()"
      case t            ⇒ s"in->$t()"
    }

    case t : ConstantLengthArrayType ⇒ s"((skill::fieldTypes::ConstantLengthArray*)$typ)->read<${mapType(t.getBaseType)}>(*in)"
    case t : VariableLengthArrayType ⇒ s"((skill::fieldTypes::VariableLengthArray*)$typ)->read<${mapType(t.getBaseType)}>(*in)"
    case t : ListType                ⇒ s"((skill::fieldTypes::ListType*)$typ)->read<${mapType(t.getBaseType)}>(*in)"
    case t : SetType                 ⇒ s"((skill::fieldTypes::SetType*)$typ)->read<${mapType(t.getBaseType)}>(*in)"

    case t : MapType ⇒
      s"""nullptr;
                ${mapType(t)} m = new ${newMapType(t.getBaseTypes.toList)};
                const auto t1 = (skill::fieldTypes::MapType*)type;

                for(auto idx = in->v64(); idx > 0; idx--) {
                    auto k1 = ${readType(t.getBaseTypes.get(0), "", s"t1->key")};
                    ${readInnerMap(t.getBaseTypes.toList.tail, 2)}
                    (*m)[k1] = v1;
                }
                $accessI = m"""

    //case t : UserType ⇒ s"    val t = this.t.asInstanceOf[${storagePool(t)}]"
    case _ ⇒ s"(${mapType(t)})$typ->read(*in).${unbox(t)}"
  }

  def innerMapType(ts : List[Type]) : String = ts.map(mapType).reduceRight((k, v) ⇒ s"::skill::api::Map<$k, $v>*")
  def newMapType(ts : List[Type]) : String = {
    val s = innerMapType(ts)
    // drop last * because we want to allocate the map itself
    s.substring(0, s.length - 1)
  }

  private final def readInnerMap(ts : List[Type], depth : Int) : String = {
    if (ts.length == 1) s"auto v${depth - 1} = ${readType(ts.head, "", s"t${depth - 1}->value")};"
    else {
      s"""${innerMapType(ts)} v${depth - 1} = new ${newMapType(ts)};
                    const auto t$depth = (skill::fieldTypes::MapType*)(t${depth - 1}->value);

                    for(auto idx$depth = in->v64(); idx$depth > 0; idx$depth--) {
                        auto k$depth = ${readType(ts.head, "", s"t${depth - 1}->key")};
                        ${readInnerMap(ts.tail, depth + 1)}
                        (*v${depth - 1})[k$depth] = v$depth;
                    }"""
    }
  }

  private final def hex(t : Type, x : Long) : String = {
    val v : Long = (if (x == Long.MaxValue || x == Long.MinValue)
      x >> (64 - t.getSkillName.substring(1).toInt);
    else
      x);

    t.getSkillName match {
      case "i8"  ⇒ "0x%02X".format(v.toByte)
      case "i16" ⇒ "0x%04X".format(v.toShort)
      case "i32" ⇒ "0x%08X".format(v.toInt)
      case _     ⇒ "0x%016X".format(v)
    }
  }

  private final def makeRestriction(t : Type, r : Restriction) : String = r match {
    case r : NonNullRestriction ⇒ "::skill::restrictions::NonNull::get()"
    case r : IntRangeRestriction ⇒
      val typename = s"int${t.getSkillName.substring(1)}_t"
      s"new ::skill::restrictions::Range<$typename>(($typename)${hex(t, r.getLow)}, ($typename)${hex(t, r.getHigh)})"

    case r : FloatRangeRestriction ⇒ t.getSkillName match {
      case "f32" ⇒ s"new ::skill::restrictions::Range<float>(${r.getLowFloat}f, ${r.getHighFloat}f)"
      case "f64" ⇒ s"new ::skill::restrictions::Range<double>(${r.getLowDouble}, ${r.getHighDouble})"
    }
    case r : ConstantLengthPointerRestriction ⇒
      "::skill::restrictions::ConstantLengthPointer::get()"

    case r : FloatDefaultRestriction ⇒
      t.getSkillName match {
        case "f32" ⇒ s"new ::skill::restrictions::FieldDefault<float>(${r.getValue}f)"
        case "f64" ⇒ s"new ::skill::restrictions::FieldDefault<double>(${r.getValue})"
      }

    case r : IntDefaultRestriction ⇒
      t.getSkillName match {
        case "i8"  ⇒ s"new ::skill::restrictions::FieldDefault<int8_t>(${r.getValue})"
        case "i16" ⇒ s"new ::skill::restrictions::FieldDefault<int16_t>(${r.getValue})"
        case "i32" ⇒ s"new ::skill::restrictions::FieldDefault<int32_t>(${r.getValue})"
        case _     ⇒ s"new ::skill::restrictions::FieldDefault<int64_t>(${r.getValue}L)"
      }

    case r : NameDefaultRestriction if t.getSkillName.equals("bool") ⇒
      s"new ::skill::restrictions::FieldDefault<bool>(${r.getValue.head})"

    case r : DefaultRestriction ⇒
      println("[c++] unhandled restriction: " + r.getName);
      s"new ::skill::restrictions::FieldDefault<${mapType(t)}>(0)"

    case r : CodingRestriction ⇒
      // @note this case is filtered, as known restrictions can only be added as part of file finalization
      ???
    //      println("[c++] unhandled restriction: " + r.getName);
    //      s"""new ::skill::restrictions::Coding(
    //            (($packageName::StringKeeper *) (
    //                    (::skill::internal::StringPool *) owner->getOwner()->strings)->keeper)->${escaped(r.getValue)})"""
  }

  private final def checkRestriction(t : Type, r : Restriction) : String = r match {
    case r : NonNullRestriction ⇒ """
            if(nullptr == v) return false;"""
    case r : IntRangeRestriction ⇒
      val typename = s"int${t.getSkillName.substring(1)}_t"
      s"""
            if((($typename)${hex(t, r.getLow)}) > v || v > ($typename)${hex(t, r.getHigh)}) return false;"""

    case r : FloatRangeRestriction ⇒ t.getSkillName match {
      case "f32" ⇒ s"""
            if(${r.getLowFloat}f > v || v > ${r.getHighFloat}f) return false;"""
      case "f64" ⇒ s"""
            if(${r.getLowDouble} > v || v > ${r.getHighDouble}) return false;"""
    }

    case _ ⇒ "" // unchecked restriction
  }

  private def writeCode(accessI : String, f : Field) : String = f.getType match {
    case t : GroundType ⇒ t.getSkillName match {
      case "annotation" ⇒ s"""auto b = ::skill::box($accessI);
            type->write(out, b);"""

      case "string" ⇒ s"""const auto x = (::skill::api::String) $accessI;
            const ::skill::SKilLID v = x ? x->getID() : 0;
            if (v)
                out->v64(v);
            else
                out->i8(0);"""
      case "bool" ⇒ s"out->i8($accessI?0xff:0);"
      case _      ⇒ s"""out->${t.getSkillName}($accessI);"""
    }

    case t : UserType ⇒ s"""${mapType(t)} v = $accessI;
            if (v)
                out->v64(skillID(v));
            else
                out->i8(0);"""

    case t : ListType ⇒ s"((skill::fieldTypes::ListType*)type)->write<${mapType(t.getBaseType)}>(out, $accessI);"

    case _ ⇒ s"""auto b = ::skill::box($accessI);
            type->write(out, b);"""
  }
}
