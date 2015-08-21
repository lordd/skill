/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-15 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.ada.api.internal

import de.ust.skill.generator.ada.GeneralOutputMaker
import scala.collection.JavaConversions._
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.VariableLengthArrayType
import de.ust.skill.ir.SetType
import de.ust.skill.ir.Declaration
import de.ust.skill.ir.Field
import de.ust.skill.ir.ListType
import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.Type
import de.ust.skill.ir.MapType

trait PoolsMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make

    makeSpec
    if (!IR.isEmpty)
      makeBody
  }

  private final def makeSpec {

    val out = open(s"""skill-types-pools-${packagePrefix.replace('-', '_')}_pools.ads""")

    out.write(s"""
with Skill.Files;
with Skill.Internal.File_Parsers;
with Skill.Types;
with Skill.Types.Pools;
with Skill.Types.Pools.Sub;
with Skill.Types.Vectors;

with $PackagePrefix;

-- instantiated pool packages
-- GNAT Bug workaround; should be "new Base(...)" instead
package Skill.Types.Pools.${PackagePrefix.replace('.', '_')}_Pools is
${
      (for (t ← IR) yield {
        val isBase = null == t.getSuperType
        val Name = name(t)
        val Type = PackagePrefix+"."+Name
        s"""
   package ${Name}_P is

      type Pool_T is new ${if(isBase)"Base"else"Sub"}_Pool_T with private;
      type Pool is access Pool_T;

      -- API methods
      function Get (This : access Pool_T; ID : Skill_ID_T) return $Type;

      -- constructor for instances
      procedure Make
        (This  : access Pool_T${
          (
            for (f ← t.getAllFields)
              yield s""";
         F_${name(f)} : ${mapType(f.getType)} := ${defaultValue(f)}"""
          ).mkString
        });
      function Make
        (This  : access Pool_T${
          (
            for (f ← t.getAllFields)
              yield s""";
         F_${name(f)} : ${mapType(f.getType)} := ${defaultValue(f)}"""
          ).mkString
        }) return ${mapType(t)};

      ----------------------
      -- internal methods --
      ----------------------

      -- constructor invoked by new_pool
      function Make_Pool (Type_Id : Natural${
          if (isBase) ""
          else "; Super : Skill.Types.Pools.Pool"
        }) return Pools.Pool;
      -- destructor invoked by close
      procedure Free (This : access Pool_T);

      overriding
      function Add_Field
        (This : access Pool_T;
         ID   : Natural;
         T    : Field_Types.Field_Type;
         Name : String_Access)
         return Skill.Field_Declarations.Field_Declaration;

      procedure Add_Known_Field
        (This : access Pool_T;
         Name : String_Access;
         String_Type : Field_Types.Builtin.String_Type_T.Field_Type;
         Annotation_Type : Field_Types.Builtin.Annotation_Type_P.Field_Type);

      overriding
      procedure Resize_Pool
        (This       : access Pool_T;
         Targets    : Type_Vector;
         Self_Index : Natural);

      overriding function Static_Size (This : access Pool_T) return Natural;

      -- applies F for each element in this
--        procedure Foreach
--          (This : access Pool_T;
--           F    : access procedure (I : $Name));

      package Sub_Pools is new Sub
        (T    => ${Type}_T,
         P    => $Type,
         To_P => ${PackagePrefix}.To_$Name);

      function Make_Sub_Pool
        (This : access Pool_T;
         ID   : Natural;
         Name : String_Access) return Skill.Types.Pools.Pool is
        (Sub_Pools.Make (This.To_Pool, ID, Name));

      --        function Iterator (This : access Pool_T) return Age_Iterator is abstract;

      procedure Do_For_Static_Instances (This : access Pool_T;
                                         F : access procedure(I : Annotation));

      procedure Update_After_Compress
        (This     : access Pool_T;
         Lbpo_Map : Skill.Internal.Lbpo_Map_T);

   private

      -- note: this trick makes treatment of new objects more complicated; there
      -- is an almost trivial solution to the problem in C++
      type Static_Data_Array_T is array (Positive range <>) of aliased ${Type}_T;
      type Static_Data_Array is access Static_Data_Array_T;

      package A1 is new Vectors (Natural, $Type);
      subtype New_Instance_Vector is A1.Vector;

      package A2 is new Vectors (Natural, Static_Data_Array);
      subtype Static_Instance_Vector is A2.Vector;

      type Pool_T is new ${if(isBase)"Base"else"Sub"}_Pool_T with record
         Static_Data : Static_Instance_Vector;
         New_Objects : New_Instance_Vector;
      end record;
   end ${Name}_P;
"""
      }).mkString
    }
end Skill.Types.Pools.${PackagePrefix.replace('.', '_')}_Pools;
""")

    out.close()
  }

  private final def makeBody {

    val out = open(s"""skill-types-pools-${packagePrefix.replace('-', '_')}_pools.adb""")

    out.write(s"""
with Ada.Unchecked_Conversion;
with Ada.Unchecked_Deallocation;

with Skill.Equals;
with Skill.Errors;
with Skill.Field_Types;
with Skill.Internal.Parts;
with Skill.Streams;
with Skill.String_Pools;

with $PackagePrefix.Api;
with $PackagePrefix.Internal_Skill_Names;${
      (for (t ← IR; f ← t.getFields) yield s"""
with $PackagePrefix.Known_Field_${escaped(t.getName.ada())}_${escaped(f.getName.ada())};""").mkString
    }

-- instantiated pool packages
-- GNAT Bug workaround; should be "new Base(...)" instead
package body Skill.Types.Pools.${PackagePrefix.replace('.', '_')}_Pools is
${
      (for (t ← IR) yield {
        val isBase = null == t.getSuperType
        val Name = name(t)
        val Type = PackagePrefix+"."+Name

  def mapToFieldType(f : Field) : String = {
    //@note temporary string & annotation will be replaced later on
    @inline def mapGroundType(t : Type) : String = t.getSkillName match {
      case "annotation" ⇒ "Field_Types.Field_Type(Annotation_Type)"
      case "bool" | "i8" | "i16" | "i32" | "i64" | "v64" | "f32" | "f64" ⇒
        if (f.isConstant) s"Field_Types.Builtin.Const_${t.getName.capital}(${f.constantValue})"
        else s"Field_Types.Builtin.${t.getName.capital}"
      case "string" ⇒ "Field_Types.Field_Type(String_Type)"

      case s        ⇒ s"""(FieldType<${mapType(t)}>)(owner().poolByName().get("${t.getSkillName}"))"""
    }

    f.getType match {
      case t : GroundType  ⇒ mapGroundType(t)
      //      case t : ConstantLengthArrayType ⇒ s"new ConstantLengthArray<>(${t.getLength}, ${mapGroundType(t.getBaseType)})"
      //      case t : VariableLengthArrayType ⇒ s"new VariableLengthArray<>(${mapGroundType(t.getBaseType)})"
      //      case t : ListType                ⇒ s"new ListType<>(${mapGroundType(t.getBaseType)})"
      //      case t : SetType                 ⇒ s"new SetType<>(${mapGroundType(t.getBaseType)})"
      //      case t : MapType                 ⇒ t.getBaseTypes().map(mapGroundType).reduceRight((k, v) ⇒ s"new MapType<>($k, $v)")
      case t : Declaration ⇒ s"Field_Types.Field_Type(This${if(isBase)""else".Base"}.Owner.Types_By_Name.Element(${internalSkillName(t)}))"
      case _               ⇒ "Field_Types.Field_Type(Annotation_Type)"
    }
  }

        s"""
   package body ${Name}_P is

      -- API methods
      function Get
        (This : access Pool_T;
         ID   : Skill_ID_T) return $Type
      is
      begin
         if 0 = ID then
            return null;
         else
            return ${PackagePrefix}.To_$Name (This${if(isBase)""else".Base"}.Data (ID));
         end if;
      end Get;

      procedure Make
        (This  : access Pool_T${
          (
            for (f ← t.getAllFields)
              yield s""";
         F_${name(f)} : ${mapType(f.getType)} := ${defaultValue(f)}"""
          ).mkString
        }) is
         R : ${mapType(t)} := new ${mapType(t)}_T;
      begin
         R.Skill_ID := -1;${
          (
            for (f ← t.getAllFields)
              yield s"""
         R.Set_${name(f)} (F_${name(f)});"""
          ).mkString
        }
         This.New_Objects.Append (R);
      end Make;
      function Make
        (This  : access Pool_T${
          (
            for (f ← t.getAllFields)
              yield s""";
         F_${name(f)} : ${mapType(f.getType)} := ${defaultValue(f)}"""
          ).mkString
        }) return ${mapType(t)} is
         R : ${mapType(t)} := new ${mapType(t)}_T;
      begin
         R.Skill_ID := -1;${
          (
            for (f ← t.getAllFields)
              yield s"""
         R.Set_${name(f)} (F_${name(f)});"""
          ).mkString
        }
         This.New_Objects.Append (R);
         return R;
      end Make;

      ----------------------
      -- internal methods --
      ----------------------

      -- constructor invoked by new_pool
      function Make_Pool (Type_Id : Natural${
          if (null == t.getSuperType) ""
          else "; Super : Skill.Types.Pools.Pool"
        }) return Skill.Types.Pools.Pool is
         function Convert is new Ada.Unchecked_Conversion
           (Source => Pool,
            Target => Skill.Types.Pools.Base_Pool);
         function Convert is new Ada.Unchecked_Conversion
           (Source => Pool,
            Target => Skill.Types.Pools.Pool);

         This : Pool;
      begin
         This :=
           new Pool_T'
             (Name          => ${internalSkillName(t)},
              Type_Id       => Type_Id,${
          if (null == t.getSuperType) """
              Super         => null,
              Base          => null,
              Data          => Skill.Types.Pools.Empty_Data,
              Owner         => null,"""
          else """
              Super         => Super,
              Base          => Super.Base,"""
        }
              Sub_Pools     => Sub_Pool_Vector_P.Empty_Vector,
              Data_Fields_F =>
                Skill.Field_Declarations.Field_Vector_P.Empty_Vector,
              Known_Fields => ${
          if (t.getFields.isEmpty())
            "No_Known_Fields"
          else {
            (for ((f, i) ← t.getFields.zipWithIndex)
              yield s"                 ${i + 1} => ${internalSkillName(f)}\n"
            ).mkString("new String_Access_Array'((\n", ",", "                ))")
          }
        },
              Blocks      => Skill.Internal.Parts.Blocks_P.Empty_Vector,
              Fixed       => False,
              Cached_Size => 0,
              Static_Data => A2.Empty_Vector,
              New_Objects => A1.Empty_Vector);
${
          if (null == t.getSuperType) """
         This.Base := Convert (This);"""
          else ""
        }
         return Convert (This);
      exception
         when E : others =>
            Skill.Errors.Print_Stacktrace (E);
            Skill.Errors.Print_Stacktrace;
            raise Skill.Errors.Skill_Error with "$Name pool allocation failed";
      end Make_Pool;

      procedure Free (This : access Pool_T) is

         procedure Delete
           (This : Skill.Field_Declarations.Field_Declaration)
         is
         begin
            This.Free;
         end Delete;

         procedure Delete_SA (This : Static_Data_Array) is
            type P is access all Static_Data_Array_T;
            D : P := P (This);

            procedure Free is new Ada.Unchecked_Deallocation
              (Static_Data_Array_T,
               P);
         begin
            Free (D);
         end Delete_SA;
${
              if(isBase)"""
         Data : Annotation_Array := This.Data;"""
              else ""
            }
         procedure Delete is new Ada.Unchecked_Deallocation
           (Skill.Types.Skill_Object,
            Skill.Types.Annotation);
         procedure Delete is new Ada.Unchecked_Deallocation
           (Skill.Types.Annotation_Array_T,
            Skill.Types.Annotation_Array);

         type P is access all Pool_T;
         procedure Delete is new Ada.Unchecked_Deallocation (Pool_T, P);
         D : P := P (This);
      begin${
        if(isBase)"""
         if 0 /= Data'Length then
            Delete (Data);
         end if;"""
        else ""
      }
         This.Sub_Pools.Free;
         This.Data_Fields_F.Foreach (Delete'Access);
         This.Data_Fields_F.Free;
         This.Blocks.Free;
         This.Static_Data.Foreach (Delete_SA'Access);
         This.Static_Data.Free;
         This.New_Objects.Free;
         Delete (D);
      end Free;

      function Add_Field
        (This : access Pool_T;
         ID   : Natural;
         T    : Field_Types.Field_Type;
         Name : String_Access)
         return Skill.Field_Declarations.Field_Declaration
      is
         pragma Warnings (Off);

         type P is access all Pool_T;
         function Convert is new Ada.Unchecked_Conversion
           (P, Field_Declarations.Owner_T);

         F : Field_Declarations.Field_Declaration;

         type Super is access all Pool_T;
      begin
${
          t.getFields.foldRight("""
         return Super (This).Add_Field (ID, T, Name);""") {
            case (f, s) ⇒ s"""
         if Skill.Equals.Equals
             (Name,
              ${internalSkillName(f)})
         then
            F := ${PackagePrefix}.Known_Field_${escaped(t.getName.ada)}_${escaped(f.getName.ada)}.Make (ID, T, Convert (P (This)));
         else$s
         end if;"""
          }
        }${
          if (t.getFields.isEmpty()) ""
          else """

         -- TODO restrictions
         --          for (FieldRestriction<?> r : restrictions)
         --              f.addRestriction(r);
         This.Data_Fields.Append (F);

         return F;"""
        }
      end Add_Field;

      procedure Add_Known_Field
        (This        : access Pool_T;
         Name        : String_Access;
         String_Type : Field_Types.Builtin.String_Type_T.Field_Type;
         Annotation_Type : Field_Types.Builtin.Annotation_Type_P.Field_Type)
      is
         F : Field_Declarations.Field_Declaration;
      begin${
          (for (f ← t.getFields if !f.isAuto)
            yield s"""
         if Skill.Equals.Equals
             (${internalSkillName(f)},
              Name)
         then
            F :=
              This.Add_Field
              (ID => 1 + This.Data_Fields_F.Length,
               T => ${mapToFieldType(f)},
               Name => Name);
            return;
         end if;"""
          ).mkString
        }
         raise Constraint_Error
           with "generator broken in pool::add_known_field";
      end Add_Known_Field;

      procedure Resize_Pool
        (This       : access Pool_T;
         Targets    : Type_Vector;
         Self_Index : Natural)
      is
         Size : Natural;
         ID   : Skill_ID_T := 1 + Skill_ID_T (This.Blocks.Last_Element.BPO);

         Data : Skill.Types.Annotation_Array := This${if(isBase)""else".Base"}.Data;

         SD : Static_Data_Array;
         R  : $Type;

         use Interfaces;
      begin${
          if (null == t.getSuperType) """
         This.Resize_Data;"""
          else ""
        }

         if Self_Index = Targets.Length - 1
           or else Targets.Element (Self_Index + 1).Super /= This.To_Pool
         then
            Size := Natural (This.Blocks.Last_Element.Count);
         else
            Size :=
              Natural
                (Targets.Element (Self_Index + 1).Blocks.Last_Element.BPO -
                 This.Blocks.Last_Element.BPO);
         end if;

         SD := new Static_Data_Array_T (1 .. Size);
         This.Static_Data.Append (SD);

         -- set skill IDs and insert into data
         for I in SD'Range loop
            R          := SD (I).Unchecked_Access;
            R.Skill_ID := ID;
            Data (ID)  := R.To_Annotation;
            ID         := ID + 1;
         end loop;
      end Resize_Pool;

      overriding function Static_Size (This : access Pool_T) return Natural is
         Rval : Natural := This.New_Objects.Length;

         procedure Collect(This : Static_Data_Array) is
         begin
            Rval := Rval + This'Length;
         end Collect;

      begin
         This.Static_Data.Foreach(Collect'Access);
         return Rval;
      end Static_Size;

      procedure Do_For_Static_Instances
        (This : access Pool_T;
         F    : access procedure (I : Annotation))
      is
         type T is access procedure (I : ${mapType(t)});
         type U is access procedure (I : Annotation);

         function Cast is new Ada.Unchecked_Conversion(U, T);

         procedure Defer (arr : Static_Data_Array) is
         begin
            for I in arr'Range loop
               F (arr (I).To_Annotation);
            end loop;
         end Defer;
      begin
         This.Static_Data.Foreach (Defer'Access);
         This.New_Objects.Foreach(Cast(F));
      end Do_For_Static_Instances;

      procedure Update_After_Compress
        (This     : access Pool_T;
         Lbpo_Map : Skill.Internal.Lbpo_Map_T)
      is
      begin
         This.Blocks.Clear;
         This.Blocks.Append
         (Skill.Internal.Parts.Block'
            (Types.v64 (Lbpo_Map (This.Type_Id)), Types.v64 (This.Size)));

         for I in 1 .. This.Sub_Pools.Length loop
            This.Sub_Pools.Element (I).Dynamic.Update_After_Compress
            (Lbpo_Map);
         end loop;
      end Update_After_Compress;

   end ${Name}_P;
"""
      }).mkString
    }
end Skill.Types.Pools.${PackagePrefix.replace('.', '_')}_Pools;
""")

    out.close()
  }
}
