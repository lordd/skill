/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-15 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.ada

import de.ust.skill.ir._
import scala.collection.JavaConversions._

trait PackageSpecMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val out = open(s"""${packagePrefix}.ads""")

    out.write(s"""
with Ada.Containers.Indefinite_Hashed_Maps;
with Ada.Containers.Indefinite_Vectors;
with Ada.Containers.Vectors;
with Ada.Strings.Hash;
with Ada.Unchecked_Conversion;
with Interfaces;
${
      var output = ""
      /**
       * Imports the Ada containers for compound types, if necessary.
       */
      for (d ← IR) {
        var doublyLinkedListNeeded = false;
        var hashedSetsNeeded = false;
        var hashedMapsNeeded = false;

        d.getFields.filter({ f ⇒ !f.isIgnored }).foreach({ f ⇒
          f.getType match {
            case t : ListType ⇒
              if (!doublyLinkedListNeeded) {
                output += "with Ada.Containers.Doubly_Linked_Lists;\r\n"
                doublyLinkedListNeeded = true
              }
            case t : SetType ⇒
              if (!hashedSetsNeeded) {
                output += "with Ada.Containers.Hashed_Sets;\r\n"
                hashedSetsNeeded = true
              }
            case t : MapType ⇒
              if (!hashedMapsNeeded) {
                output += "with Ada.Containers.Hashed_Maps;\r\n"
                hashedMapsNeeded = true
              }
            case _ ⇒ null
          }
        })
      }
      output
    }
--
--  This package provides the user types, the accessor functions to the fields,
--  the skill state and the help functions (hash / comparison) for compound
--  types.
--

package ${packagePrefix.capitalize} is

   type String_Access is access String;

   ----------------------
   --  Built-In Types  --
   ----------------------

   type i8 is new Interfaces.Integer_8;
   type i16 is new Interfaces.Integer_16;
   subtype Short is i16;
   type i32 is new Interfaces.Integer_32;
   type i64 is new Interfaces.Integer_64;
   subtype v64 is i64;
   subtype Long is i64;
   type f32 is new Interfaces.IEEE_Float_32;
   subtype Float is f32;
   type f64 is new Interfaces.IEEE_Float_64;
   subtype Double is f64;

   type Skill_State is limited private;
   type Skill_Type is abstract tagged private;
   type Skill_Type_Access is access all Skill_Type'Class;
   function Hash (Element : Skill_Type_Access) return Ada.Containers.Hash_Type;

   -------------
   --  Utils  --
   -------------

   function Hash (Element : Short_Short_Integer) return Ada.Containers.Hash_Type;
   function Hash (Element : Short) return Ada.Containers.Hash_Type;
   function Hash (Element : Integer) return Ada.Containers.Hash_Type;
   function Hash (Element : Long) return Ada.Containers.Hash_Type;
   function Hash (Element : String_Access) return Ada.Containers.Hash_Type;
   function "=" (Left, Right : String_Access) return Boolean;

   -----------------------
   --  Specified Types  --
   -----------------------

${
      /**
       * Provides the user types.
       */
      var output = "";
      for (d ← IR) {
        output += comment(d)
        output += s"""   type ${name(d)}_Type is new Skill_Type with private;\r\n"""
        output += s"""   type ${name(d)}_Type_Access is access all ${name(d)}_Type;\r\n"""
        output += s"""   type ${name(d)}_Type_Array is array (Natural range <>) of ${name(d)}_Type_Access;\r\n"""
        output += s"""   type ${name(d)}_Type_Accesses is access ${name(d)}_Type_Array;\r\n"""
        output += s"""   function Hash (Element : ${name(d)}_Type_Access) return Ada.Containers.Hash_Type;\r\n\r\n"""
      }

      output.stripLineEnd

      /**
       * Provides the compound types.
       */
      for (d ← IR) {
        d.getFields.filter({ f ⇒ !f.isIgnored }).foreach({ f ⇒
          f.getType match {
            case t : ConstantLengthArrayType ⇒
              output += s"   type ${mapType(f.getType, d, f)} is array (1 .. ${t.getLength}) of ${mapType(t.getBaseType, d, f)};\r\n"
            case t : VariableLengthArrayType ⇒
              output += s"   package ${mapType(f.getType, d, f).stripSuffix(".Vector")} is new Ada.Containers.Vectors (Positive, ${mapType(t.getBaseType, d, f)});\r\n"
            case t : ListType ⇒
              output += s"""   package ${mapType(f.getType, d, f).stripSuffix(".List")} is new Ada.Containers.Doubly_Linked_Lists (${mapType(t.getBaseType, d, f)}, "=");\r\n"""
            case t : SetType ⇒
              output += s"""   package ${mapType(f.getType, d, f).stripSuffix(".Set")} is new Ada.Containers.Hashed_Sets (${mapType(t.getBaseType, d, f)}, Hash, "=");\r\n"""
            case t : MapType ⇒ {
              val types = t.getBaseTypes().reverse
              types.slice(0, types.length - 1).zipWithIndex.foreach({
                case (t, i) ⇒
                  val x = {
                    if (0 == i)
                      mapType(types.get(i), d, f)
                    else
                      s"""${mapType(f.getType, d, f).stripSuffix(".Map")}_${types.length - i}.Map"""
                  }

                  output += s"""   package ${mapType(f.getType, d, f).stripSuffix(".Map")}_${types.length - (i + 1)} is new Ada.Containers.Hashed_Maps (${mapType(types.get(i + 1), d, f)}, ${x}, Hash, "=");\r\n"""
                  output += s"""   function "=" (Left, Right : ${mapType(f.getType, d, f).stripSuffix(".Map")}_${types.length - (i + 1)}.Map) return Boolean renames ${mapType(f.getType, d, f).stripSuffix(".Map")}_${types.length - (i + 1)}."=";\r\n""";
              })
              output += s"""   package ${mapType(f.getType, d, f).stripSuffix(".Map")} renames ${mapType(f.getType, d, f).stripSuffix(".Map")}_1;\r\n"""
            }
            case _ ⇒ null
          }
        })
      }

      output + s"""
   ------------------------------
   --  Field Access Functions  --
   ------------------------------
${

        /**
         * Provides the accessor functions to the fields of every type.
         */
        (for (
          d ← IR;
          f ← d.getAllFields if !f.isIgnored()
        ) yield s"""
${comment(f)}   function Get_${name(f)} (Object : ${name(d)}_Type) return ${mapType(f.getType, f.getDeclaredIn, f)};
${
          if (f.isConstant) ""
          else {
            s"""${comment(f)}   procedure Set_${name(f)} (
      Object : in out ${name(d)}_Type;
      Value  :        ${mapType(f.getType, f.getDeclaredIn, f)}
   );
"""
          }
        }
""").mkString
      }"""
    }
private

   type Skill_States is (Unused, Append, Create, Read, Write);
   Skill_State_Error : exception;

   type Skill_Type is abstract tagged
      record
         skill_id : Natural;
      end record;
${
      /**
       * Provides the record types of the type declarations.
       */
      (for (d ← IR) yield {
        val superType = if (d.getSuperType == null) "Skill" else name(d.getSuperType)

        s"""
   type ${name(d)}_Type is new ${superType}_Type with
      record
${
          val fields = d.getFields.filterNot { f ⇒ f.isConstant() || f.isIgnored() }
          if (fields.size <= 0) s"""         null;"""
          else
            (for (f ← fields) yield {
              val comment = if (f.isAuto()) "  --  auto aka not serialized" else ""
              s"""         ${escapedLonely(f.getSkillName)} : ${mapType(f.getType, d, f)};${comment}"""
            }).mkString("\n")
        }
      end record;
"""
      }).mkString
    }
   ------------------
   --  STRING POOL --
   ------------------
   package String_Pool_Vector is new Ada.Containers.Indefinite_Vectors (Positive, String);
   type String_Pool_Access is access String_Pool_Vector.Vector;

   --------------------
   --  STORAGE POOL  --
   --------------------
   package Storage_Pool_Vector is new Ada.Containers.Vectors (Positive, Skill_Type_Access);

   --------------------------
   --  FIELD DECLARATIONS  --
   --------------------------
   --
   --  The base type vector should be replaced by a variant field declaration
   --  record. The problem in the first place was that the variable Field_Type
   --  (in File_Reader and -_Writer) needs to be a constant. The problem could
   --  not be solved by adding the constant declaration to the variable.
   --
   package Base_Types_Vector is new Ada.Containers.Vectors (Positive, Long);

   type Field_Declaration (Size : Positive) is
      record
         id                    : Long;
         Name                  : String (1 .. Size);
         F_Type                : Long;
         Constant_Value        : Long;
         Constant_Array_Length : Long;
         Base_Types            : Base_Types_Vector.Vector;
         Known                 : Boolean;
         Written               : Boolean;
      end record;
   type Field_Information is access Field_Declaration;

   package Fields_Vector is new Ada.Containers.Vectors (Positive, Field_Information);

   -------------------------
   --  TYPE DECLARATIONS  --
   -------------------------
   type Type_Declaration (Type_Size : Positive; Super_Size : Natural) is
      record
         id           : Long;
         Name         : String (1 .. Type_Size);
         Super_Name   : String (1 .. Super_Size);
         spsi         : Natural;
         lbpsi        : Natural;
         Fields       : Fields_Vector.Vector;
         Storage_Pool : Storage_Pool_Vector.Vector;
         Known        : Boolean;
         Written      : Boolean;
      end record;
   type Type_Information is access Type_Declaration;

   package Types_Hash_Map is new Ada.Containers.Indefinite_Hashed_Maps
      (String, Type_Information, Ada.Strings.Hash, "=");
   type Types_Hash_Map_Access is access Types_Hash_Map.Map;

   -------------------
   --  SKILL STATE  --
   -------------------
   type Skill_State is
      record
         File_Name   : String_Access;
         State       : Skill_States          := Unused;
         String_Pool : String_Pool_Access    := new String_Pool_Vector.Vector;
         Types       : Types_Hash_Map_Access := new Types_Hash_Map.Map;
      end record;

end ${packagePrefix.capitalize};
""")

    out.close()
  }
}
