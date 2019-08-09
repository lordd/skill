/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-16 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.jforeign.internal

import scala.collection.JavaConversions.asScalaBuffer

import de.ust.skill.generator.jforeign.GeneralOutputMaker
import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.InterfaceType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.SetType
import de.ust.skill.ir.SingleBaseTypeContainer
import de.ust.skill.ir.Type
import de.ust.skill.ir.UserType

trait FieldDeclarationMaker extends GeneralOutputMaker {

  abstract override def make {
    super.make

    for (t ← IR; f ← t.getFields) {
      val tIsBaseType = t.getSuperType == null

      val nameT = mapType(t)
      val nameF = s"KnownField_${name(t)}_${name(f)}"

      // casting access to data array using index i
      val dataAccessI = if (null == t.getSuperType) "data[i]" else s"((${mapType(t)})data[i])"

      val out = files.open(s"internal/$nameF.java")
      //package
      out.write(s"""package ${packagePrefix}internal;

import java.io.IOException;
import java.util.Iterator;

import de.ust.skill.common.jforeign.internal.*;
import de.ust.skill.common.jforeign.internal.fieldDeclarations.*;
import de.ust.skill.common.jforeign.internal.fieldTypes.Annotation;
import de.ust.skill.common.jforeign.internal.fieldTypes.ListType;
import de.ust.skill.common.jforeign.internal.fieldTypes.MapType;
import de.ust.skill.common.jforeign.internal.fieldTypes.SetType;
import de.ust.skill.common.jforeign.internal.fieldTypes.SingleArgumentType;
import de.ust.skill.common.jforeign.internal.fieldTypes.StringType;
import de.ust.skill.common.jforeign.internal.fieldTypes.V64;
import de.ust.skill.common.jforeign.internal.parts.Block;
import de.ust.skill.common.jforeign.internal.parts.Chunk;
import de.ust.skill.common.jforeign.internal.parts.SimpleChunk;
import de.ust.skill.common.jforeign.iterators.IterableArrayView;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

""")

      out.write(s"""
/**
 * ${f.getType.toString} ${t.getName.capital}.${f.getName.camel}
 */
${
        suppressWarnings
      }final class $nameF extends ${
        if (f.isAuto) "AutoField"
        else "FieldDeclaration"
      }<${mapType(f, true)}, ${mapType(t)}> implements
               ${
        f.getType match {
          case ft : GroundType ⇒ ft.getSkillName match {
            case "bool"                  ⇒ s"""KnownBooleanField<${mapType(t)}>"""
            case "i8"                    ⇒ s"""KnownByteField<${mapType(t)}>"""
            case "i16"                   ⇒ s"""KnownShortField<${mapType(t)}>"""
            case "i32"                   ⇒ s"""KnownIntField<${mapType(t)}>"""
            case "i64" | "v64"           ⇒ s"""KnownLongField<${mapType(t)}>"""
            case "f32"                   ⇒ s"""KnownFloatField<${mapType(t)}>"""
            case "f64"                   ⇒ s"""KnownDoubleField<${mapType(t)}>"""
            case "annotation" | "string" ⇒ s"""KnownField<${mapType(f.getType)}, ${mapType(t)}>"""
            case ft                      ⇒ "???missing specialization for type " + ft
          }
          case _ ⇒ s"""KnownField<${mapType(f, false)}, ${mapType(t)}>"""
        }
      }${
        // mark ignored fields as ignored; read function is inherited
        if (f.isIgnored()) ", IgnoredField"
        else ""
      }${
        // mark interface fields
        if (f.getType.isInstanceOf[InterfaceType]) ", InterfaceField"
        else ""
      } {

    public $nameF(FieldType<${mapType(f, true)}> type, ${
        if (f.isAuto()) ""
        else "int index, "
      }${name(t)}Access owner) {
        super(type, "${f.getName.getInternalName}", ${
        if (f.isAuto()) "0"
        else "index"
      }, owner);
            // TODO insert known restrictions?
    }
${
        if (f.isAuto) "" else s"""
    @Override
    public void read(ChunkEntry ce) {${
          if (f.isConstant())
            """
        // this field is constant"""
          else
            s"""
        final MappedInStream in = ce.in;
        final Chunk last = ce.c;
        final Iterator<$nameT> is;
        if (last instanceof SimpleChunk) {
            SimpleChunk c = (SimpleChunk) last;
            is = ((${name(t)}Access) owner).dataViewIterator((int) c.bpo, (int) (c.bpo + c.count));
        } else
            is = owner.iterator();
${
              // preparation code
              f.getType match {
                case t : GroundType if "string".equals(t.getSkillName) ⇒ s"""
        final StringPool sp = (StringPool)owner.owner().Strings();"""
                case t : InterfaceType if t.getSuperType.getSkillName != "annotation" ⇒ s"""
        final ${name(t.getSuperType)}Access target = (${name(t.getSuperType)}Access)${name(t.getSuperType)}Access
                .<${mapType(t.getSuperType)},${mapType(t)}>cast(type);"""
                case t : UserType ⇒ s"""
        final ${name(t)}Access target = (${name(t)}Access)type;"""
                case _ ⇒ ""
              }
            }
        int count = (int) last.count;
        while (0 != count--) {
            ${
              // read next element
              f.getType match {
                case ft : InterfaceType if ft.getSuperType.getSkillName != "annotation" ⇒
                  s"""is.next().${setterOrFieldAccess(t, f)}((${mapType(f.getType)})target.getByID(in.v64()));"""

                case ft : InterfaceType ⇒
                  s"""is.next().set${escaped(f.getName.capital)}((${mapType(f.getType)})type.readSingleField(in));"""

                case ft : GroundType ⇒ ft.getSkillName match {
                  case "annotation" ⇒ s"""is.next().${setterOrFieldAccess(t, f)}(type.readSingleField(in));"""
                  case "string"     ⇒ s"""is.next().${setterOrFieldAccess(t, f)}(sp.get(in.v64()));"""
                  case _            ⇒ s"""is.next().${setterOrFieldAccess(t, f)}(in.${ft.getName.getInternalName}());"""
                }

                case ft : UserType ⇒ s"""is.next().${setterOrFieldAccess(t, f)}(target.getByID(in.v64()));"""
                case ft : ListType ⇒ s"""${rc.map(f).getName}<${mapType(ft.getBaseType, true)}> l = new ${
                  val actual = rc.map(f).getName
                  if (actual == "java.util.List") s"java.util.LinkedList" else actual
                }<>();
            ((ListType)type).readSingleField(in, l);
            is.next().${setterOrFieldAccess(t, f)}(l);"""
                case ft : SetType ⇒ s"""${rc.map(f).getName}<${mapType(ft.getBaseType, true)}> s = new ${
                  val actual = rc.map(f).getName
                  if (actual == "java.util.Set") s"java.util.HashSet" else actual
                }<>();
            ((SetType)type).readSingleField(in, s);
            is.next().${setterOrFieldAccess(t, f)}(s);"""
                case ft : MapType ⇒ {
                  val last :: rest = ft.getBaseTypes.reverse.toList
                  val actualType : String = rest.foldLeft(mapType(last, true))((acc, curr) ⇒ s"${rc.map(f).getName}<${mapType(curr, true)}, ${acc}>")

                  s"""$actualType m = new ${
                    val actual = rc.map(f).getName
                    if (actual == "java.util.Map") s"java.util.HashMap" else actual
                  }<>();
            ((MapType)type).readSingleField(in, m);
            is.next().${setterOrFieldAccess(t, f)}(m);"""
                }
                case _ ⇒ s"""is.next().${setterOrFieldAccess(t, f)}(type.readSingleField(in));"""
              }
            }
        }"""
        }
    }

    @Override
    public long offset() {${
          if (f.isConstant())
            """
        return 0; // this field is constant"""
          else """
        final Block range = owner.lastBlock();""" + {
            // this prelude is common to most cases
            def preludeData : String =
              s"""final ${mapType(t.getBaseType)}[] data = ((${name(t.getBaseType)}Access) owner.basePool()).data();
        long result = 0L;
        int i = null == range ? 0 : (int) range.bpo;
        final int high = null == range ? data.length : (int) (range.bpo + range.count);
        for (; i < high; i++) {"""

            def offsetCode(fieldType : Type) : String = fieldType match {

              // read next element
              case fieldType : GroundType ⇒ fieldType.getSkillName match {

                case "annotation" ⇒ s"""
        final Annotation t = Annotation.cast(type);
        $preludeData
            ${mapType(f.getType)} v = $dataAccessI.${getterOrFieldAccess(t, f)};
            if(null==v)
                result += 2;
            else
                result += t.singleOffset(v${if (f.getType.isInstanceOf[InterfaceType]) ".self()" else ""});
        }
        return result;"""

                case "string" ⇒ s"""
        final StringType t = (StringType) type;
        $preludeData
            String v = (${if (tIsBaseType) "" else s"(${mapType(t)})"}data[i]).${getterOrFieldAccess(t, f)};
            if(null==v)
                result++;
            else
                result += t.singleOffset(v);
        }
        return result;"""

                case "i8" | "bool" ⇒ s"""
        return range.count;"""

                case "i16" ⇒ s"""
        return 2 * range.count;"""

                case "i32" | "f32" ⇒ s"""
        return 4 * range.count;"""

                case "i64" | "f64" ⇒ s"""
        return 8 * range.count;"""

                case "v64" ⇒ s"""
        $preludeData
            long v = (${if (tIsBaseType) "" else s"(${mapType(t)})"}data[i]).get${escaped(f.getName.capital)}();

            if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
                result += 1;
            } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
                result += 2;
            } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
                result += 3;
            } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
                result += 4;
            } else if (0L == (v & 0xFFFFFFF800000000L)) {
                result += 5;
            } else if (0L == (v & 0xFFFFFC0000000000L)) {
                result += 6;
            } else if (0L == (v & 0xFFFE000000000000L)) {
                result += 7;
            } else if (0L == (v & 0xFF00000000000000L)) {
                result += 8;
            } else {
                result += 9;
            }
        }
        return result;"""
                case _ ⇒ s"""
        throw new NoSuchMethodError();"""
              }

              case fieldType : ConstantLengthArrayType ⇒ s"""
        final SingleArgumentType<${mapType(fieldType)}, ${mapType(fieldType.getBaseType, true)}> t =
            (SingleArgumentType<${mapType(fieldType)}, ${mapType(fieldType.getBaseType, true)}>) type;

        final FieldType<${mapType(fieldType.getBaseType, true)}> baseType = t.groundType;
        $preludeData
            final ${mapType(f.getType)} v = (${if (tIsBaseType) "" else s"(${mapType(t)})"}data[i]).get${escaped(f.getName.capital)}();
            assert null==v;
            result += baseType.calculateOffset(v);
        }
        return result;"""

              case fieldType : SingleBaseTypeContainer ⇒ s"""
        final SingleArgumentType<${mapType(f, true)}, ${mapType(fieldType.getBaseType, true)}> t =
            (SingleArgumentType<${mapType(f, true)}, ${mapType(fieldType.getBaseType, true)}>) type;

        final FieldType<${mapType(fieldType.getBaseType, true)}> baseType = t.groundType;
        $preludeData
            final ${mapType(f, false)} v = (${if (tIsBaseType) "" else s"(${mapType(t)})"}data[i]).${getterOrFieldAccess(t, f)};
            if(null==v)
                result++;
            else {
                result += V64.singleV64Offset(v.size());
                result += baseType.calculateOffset(v);
            }
        }
        return result;"""

              case fieldType : MapType ⇒ s"""
        final MapType t = (MapType) type;
        final FieldType keyType = t.keyType;
        final FieldType valueType = t.valueType;
        $preludeData
            final ${mapType(f, false)} v = (${
                if (tIsBaseType) ""
                else s"(${mapType(t)})"
              }data[i]).${getterOrFieldAccess(t, f)};
            if(null==v)
                result++;
            else {
                result += V64.singleV64Offset(v.size());
                result += keyType.calculateOffset(v.keySet());
                result += valueType.calculateOffset(v.values());
            }
        }
        return result;"""

              case fieldType : UserType ⇒ s"""
        $preludeData
            final ${mapType(f.getType)} instance = $dataAccessI.${getterOrFieldAccess(t, f)};
            if (null == instance) {
                result += 1;
                continue;
            }
            long v = instance${if (f.getType.isInstanceOf[InterfaceType]) ".self()" else ""}.getSkillID();

            if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
                result += 1;
            } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
                result += 2;
            } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
                result += 3;
            } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
                result += 4;
            } else if (0L == (v & 0xFFFFFFF800000000L)) {
                result += 5;
            } else if (0L == (v & 0xFFFFFC0000000000L)) {
                result += 6;
            } else if (0L == (v & 0xFFFE000000000000L)) {
                result += 7;
            } else if (0L == (v & 0xFF00000000000000L)) {
                result += 8;
            } else {
                result += 9;
            }
        }
        return result;"""

              case fieldType : InterfaceType ⇒ offsetCode(fieldType.getSuperType)

              case _ ⇒ s"""
        throw new NoSuchMethodError();"""
            }

            offsetCode(f.getType)
          }
        }
    }

    @Override
    public void write(MappedOutStream out) throws IOException {${
          if (f.isConstant())
            """
        // this field is constant"""
          else
            s"""
        ${mapType(t.getBaseType)}[] data = ((${name(t.getBaseType)}Access) owner.basePool()).data();
        int i;
        final int high;

        final Chunk last = dataChunks.getLast().c;
        if (last instanceof SimpleChunk) {
            SimpleChunk c = (SimpleChunk) last;
            i = (int) c.bpo;
            high = (int) (c.bpo + c.count);
        } else {${
              // we have to use the offset of the pool
              if (tIsBaseType) """
            i = 0;
            high = owner.size();
        """
              else """
            i = owner.size() > 0 ? (int) owner.iterator().next().getSkillID() - 1 : 0;
            high = i + owner.size();
        """
            }}

        for (; i < high; i++) {
            ${
              // read next element
              f.getType match {
                case ft : GroundType ⇒ ft.getSkillName match {
                  case "annotation" | "string" ⇒ s"""type.writeSingleField($dataAccessI.${getterOrFieldAccess(t, f)}, out);"""
                  case _                       ⇒ s"""out.${ft.getName.getInternalName}($dataAccessI.${getterOrFieldAccess(t, f)});"""
                }

                case ft : UserType ⇒ s"""${mapType(ft)} v = $dataAccessI.${getterOrFieldAccess(t, f)};
            if (null == v)
                out.i8((byte) 0);
            else
                out.v64(v.getSkillID());"""
                case _ ⇒ s"""type.writeSingleField($dataAccessI.${getterOrFieldAccess(t, f)}, out);"""
              }
            }
        }"""
        }
    }
"""
      }
    @Override
    public ${mapType(f, true)} getR(SkillObject ref) {
        ${
        if (f.isConstant()) s"return ${mapType(t)}.${getterOrFieldAccess(t, f)};"
        else s"return ((${mapType(t)}) ref).${getterOrFieldAccess(t, f)};"
      }
    }

    @Override
    public void setR(SkillObject ref, ${mapType(f, true)} value) {
        ${
        if (f.isConstant()) s"""throw new IllegalAccessError("${f.getName.camel} is a constant!");"""
        else s"((${mapType(t)}) ref).${setterOrFieldAccess(t, f)}(value);"
      }
    }

    @Override
    public ${mapType(f, false)} get(${mapType(t)} ref) {
        ${
        if (f.isConstant()) s"return ${mapType(t)}.${getterOrFieldAccess(t, f)};"
        else s"return ref.${getterOrFieldAccess(t, f)};"
      }
    }

    @Override
    public void set(${mapType(t)} ref, ${mapType(f, false)} value) {
        ${
        if (f.isConstant()) s"""throw new IllegalAccessError("${f.getName.camel} is a constant!");"""
        else s"ref.${setterOrFieldAccess(t, f)}(value);"
      }
    }
}
""")
      out.close()
    }
  }
}
