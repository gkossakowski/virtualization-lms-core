package scala.virtualization.lms
package epfl
package test7

import common._
import original._
import original.Conversions._

trait MDArrayBaseExp extends MDArrayBase with BaseExp with IfThenElseExp {
  /*
      Some counters
   */
  var nList: Int = 0
  var nArray: Int = 0
  var nValue: Int = 0

  /*
      Basic AST building blocks
   */
  case class KnownAtRuntime[A: ClassManifest](name: String, value: Boolean) extends RichDef[MDArray[A]](Nil) { override def getString() = "KnownAtRuntime(" + name + ") " }
  case class KnownAtCompileTime[A: ClassManifest](value: MDArray[A]) extends RichDef[MDArray[A]](Nil) {
    override def getString() = "KnownAtCompileTime(" + value.toString + ")"
    // TODO: Understand why without this override the definition always collapses to a single value...
    override def equals(other: Any) = other match {
      case t: KnownAtCompileTime[_] => t.canEqual(this) && t.value == this.value
      case _ => false }
  }

  // With
  case class GenArrayWith[A: ClassManifest](lExpr: List[Exp[MDArray[A]]], shp: Exp[MDArray[Int]]) extends RichDef[MDArray[A]](shp::lExpr) { override def getString() = "GenArrayWith(" + shp.toString + " - " + lExpr.mkString(", ") + ")" }
  case class ModArrayWith[A: ClassManifest](lExpr: List[Exp[MDArray[A]]], a: Exp[MDArray[A]]) extends RichDef[MDArray[A]](a::lExpr) { override def getString() = "ModArrayWith(" + a.toString + " - " + lExpr.mkString(", ") + ")" }
  // TODO: Important implicit assumption made here -- we assume foldFunction has no outside dependencies. According to the SAC spec, it should indeed be the case, but proving it would be better
  case class FoldArrayWith[A: ClassManifest](wExpr: Exp[MDArray[A]], neutral: Exp[MDArray[A]], foldFunction: (Exp[MDArray[A]], Exp[MDArray[A]]) => Exp[MDArray[A]]) extends RichDef[MDArray[A]](neutral::wExpr::Nil) { override def getString() = "FoldArrayWith(" + neutral + ", " + foldFunction + ", " + wExpr + ")" }
  case class IndexVector(lb: Exp[MDArray[Int]], lbStrict: Exp[Boolean], ub: Exp[MDArray[Int]], ubStrict: Exp[Boolean], step: Exp[MDArray[Int]], width: Exp[MDArray[Int]]) extends RichDef[MDArray[Int]](lb::lbStrict::ub::ubStrict::step::width::Nil) { override def getString() = "IndexVector(" + lb + ", " + lbStrict + ", " + ub + ", " + ubStrict + ", " + step + ", " + width + ")" }
  case class WithNode[A: ClassManifest](iv: Exp[MDArray[Int]], expr: Exp[MDArray[A]]) extends RichDef[MDArray[A]](iv::expr::Nil) { override def getString() = "With(" + iv.toString + " => " + expr.toString + ")" }

  // Base functions
  case class ToDim[A: ClassManifest](a: Exp[MDArray[A]]) extends RichDef[Int](a::Nil) { override def getString() = "Dim(" + a + ")" }
  case class ToShape[A: ClassManifest](a: Exp[MDArray[A]]) extends RichDef[MDArray[Int]](a::Nil) { override def getString() = "Shape(" + a + ")" }
  case class Reshape[A: ClassManifest](shp: Exp[MDArray[Int]], a: Exp[MDArray[A]]) extends RichDef[MDArray[A]](shp::a::Nil) { override def getString() = "Reshape(" + shp + ", " + a + ")" }
  case class Sel[A: ClassManifest](iv: Exp[MDArray[Int]], a: Exp[MDArray[A]]) extends RichDef[MDArray[A]](iv::a::Nil) { override def getString() = "Sel(" + iv + ", " + a + ")" }
  case class Cat[A: ClassManifest](d: Exp[Int], a: Exp[MDArray[A]], b: Exp[MDArray[A]]) extends RichDef[MDArray[A]](d::a::b::Nil) { override def getString() = "Cat(" + d + ", " + a + ", " + b + ")" }
  case class Reduce[A: ClassManifest, B](z: Exp[B], a: Exp[MDArray[A]], op: (B,A)=>B, opName: String) extends RichDef[A](z::a::Nil) { override def getString() = "Reduce(" + z + ", " + opName + ", " + a + ")" }

  // Conversions within the staged universe
  case class FromList[A: ClassManifest](value: Exp[List[A]]) extends RichDef[MDArray[A]](value::Nil) { override def getString() = "FromList(" + value.toString + ")" }
  case class FromArray[A: ClassManifest](value: Exp[Array[A]]) extends RichDef[MDArray[A]](value::Nil) { override def getString() = "FromArray(" + value.toString + ")" }
  case class FromValue[A: ClassManifest](value: Exp[A]) extends RichDef[MDArray[A]](value::Nil) { override def getString() = "FromValue(" + value.toString + ")" }

  // Going back to the real world
  case class ToList[A: ClassManifest](value: Exp[MDArray[A]]) extends RichDef[List[A]](value::Nil) { override def getString() = "ToList(" + value.toString + ")" }
  case class ToArray[A: ClassManifest](value: Exp[MDArray[A]]) extends RichDef[Array[A]](value::Nil) { override def getString() = "ToArray(" + value.toString + ")" }
  case class ToValue[A: ClassManifest](value: Exp[MDArray[A]]) extends RichDef[A](value::Nil) { override def getString() = "ToValue(" + value.toString + ")" }

  // Operations and Values
  // XXX: In an optimization phase, operations and values will end up as With loops => the only reason to keep them
  // as independent operations are to have more powerful constraints in the typing system
  case class InfixOpAA[A: ClassManifest, B: ClassManifest](array1: Exp[MDArray[A]], array2: Exp[MDArray[A]], op: (A, A) => B, opName: String) extends RichDef[MDArray[B]](array1::array2::Nil) { override def getString() = "InfixOpAA(" + opName + ": " + array1 + " and " + array2 + ")" }
  case class InfixOpAE[A: ClassManifest, B: ClassManifest](array: Exp[MDArray[A]], element: Exp[A], op: (A, A) => B, opName: String) extends RichDef[MDArray[B]](array::element::Nil) { override def getString() = "InfixOpAE(" + opName + ": " + array + " and " + element + ")" }
  case class UnaryOp[A: ClassManifest, B: ClassManifest](array: Exp[MDArray[A]], op: A => B, opName: String) extends RichDef[MDArray[B]](array::Nil) { override def getString() = "UnaryOp(" + opName + ": " + array + ")" }
  case class Where[A: ClassManifest](cond: Exp[MDArray[Boolean]], array1: Exp[MDArray[A]], array2: Exp[MDArray[A]]) extends RichDef[MDArray[A]](cond::array1::array2::Nil) { override def getString() = "Where(" + cond + ", " + array1 + ", " + array2 + ")" }
  case class Values[A: ClassManifest](dim: Exp[Int], value: Exp[A]) extends RichDef[MDArray[A]](dim::value::Nil) { override def getString() = "Values(" + value + ", " + dim + ")"}

  // Nothing
  case object Nothing extends Def[MDArray[Int]] { override def toString() = "<null>" }

  /*
      Abstract function implementation
   */
  // Implicit conversions
  implicit def convertFromListRep[A: ClassManifest](a: List[A]): Exp[MDArray[A]] = KnownAtCompileTime(a)
  implicit def convertFromArrayRep[A: ClassManifest](a: Array[A]): Exp[MDArray[A]] = KnownAtCompileTime(a)
  implicit def convertFromValueRep[A: ClassManifest](a: A): Exp[MDArray[A]] = KnownAtCompileTime(a)

  // Implicit conversions from unknown elements
  implicit def convertFromListRepRep[A: ClassManifest](a: Exp[List[A]]): Exp[MDArray[A]] = FromList(a)
  implicit def convertFromArrayRepRep[A: ClassManifest](a: Exp[Array[A]]): Exp[MDArray[A]] = FromArray(a)
  implicit def convertFromValueRepRep[A: ClassManifest](a: Exp[A]): Exp[MDArray[A]] = FromValue(a)

  // TODO: Add type assertions here for the array
  // TODO: Change the assertions mechanism such that it can accomomdate type assertions on non-MDArray objects
  implicit def convertToListRep[A: ClassManifest](a: Exp[MDArray[A]]): Exp[List[A]] = ToList(a)
  implicit def convertToArrayRep[A: ClassManifest](a: Exp[MDArray[A]]): Exp[Array[A]] = ToArray(a)
  implicit def convertToValueRep[A: ClassManifest](a: Exp[MDArray[A]]): Exp[A] = ToValue(a)

  // Explicit conversions for elements known only at runtime
  def knownOnlyAtRuntime[A](a: List[A])(implicit mf: ClassManifest[A], o1: Overloaded1): Exp[MDArray[A]] = KnownAtRuntime[A]("list " + {nList = nList + 1; nList}, false)
  def knownOnlyAtRuntime[A](a: Array[A])(implicit mf: ClassManifest[A], o2: Overloaded2): Exp[MDArray[A]] = KnownAtRuntime[A]("array " + {nArray = nArray + 1; nArray}, false)
  def knownOnlyAtRuntime[A](a: A)(implicit mf: ClassManifest[A], o3: Overloaded3): Exp[MDArray[A]] = KnownAtRuntime[A]("value " + {nValue = nValue + 1; nValue}, true)

  // Basic operations
  def dim[A: ClassManifest](a: Exp[MDArray[A]]): Exp[Int] = ToDim(a)
  def shape[A: ClassManifest](a: Exp[MDArray[A]]): Exp[MDArray[Int]] = ToShape(a)
  def sel[A: ClassManifest](iv: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] = Sel(iv, a)
  def reshape[A: ClassManifest](iv: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] = Reshape(iv, a)
  def cat[A: ClassManifest](d: Int, one: Exp[MDArray[A]], two: Exp[MDArray[A]]): Exp[MDArray[A]] = Cat(d, one, two)

  // Zeroes, ones and values
  def values(dim: Exp[Int], value: Exp[Int]): Exp[MDArray[Int]] = {
    // XXX: Let's make values a primitive, before with loops
    // With().GenArray(convertFromValueRepRep(dim), iv => value)
    Values(dim, value)
  }

  // Where
  def where[A: ClassManifest](p: Exp[MDArray[Boolean]], a: Exp[MDArray[A]], b: Exp[MDArray[A]]): Exp[MDArray[A]] = Where(p, a, b)

  // Restructuring operations - implemented as with-loops
  def genarray[A: ClassManifest](shp: Exp[MDArray[Int]], value: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With().GenArray(shp, iv => value)
  def modarray[A: ClassManifest](a: Exp[MDArray[A]], iv: Exp[MDArray[Int]], value: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(_lb = iv, _ub = iv).ModArray(a, iv => value)

  // TODO: Redesign these functions for lower dimensions in the given vectors, filling in with zeros or shape elements
  def take[A: ClassManifest](shp: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With().GenArray(shp, iv => sel(iv, a))
  def drop[A: ClassManifest](shp: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With().GenArray(shape(a) - shp, iv => sel(iv + shp, a))
  def tile[A: ClassManifest](sv: Exp[MDArray[Int]], ov: Exp[MDArray[Int]], a:Exp[MDArray[A]]): Exp[MDArray[A]] =
    With().GenArray(sv, iv => sel(iv + ov, a))
  def rotate[A: ClassManifest](ov: Exp[MDArray[Int]], a:Exp[MDArray[A]]): Exp[MDArray[A]] =
    With().GenArray(shape(a), iv => a(((iv - ov) + shape(a)) % shape(a)))
  def shift[A: ClassManifest](ov: Exp[MDArray[Int]], expr: A, a: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With().GenArray(shape(a), iv => if ((any((iv - ov) < zeros(dim(a)))) || (any((iv - ov) >= shape(a)))) expr else a(iv - ov))

  // Reduction operations on matrices
  def sum[A](a: Exp[MDArray[A]])(implicit ev: Numeric[A], mf: ClassManifest[A]): Exp[A] = reduce[A](ev.zero, a, ev.plus, "sum")
  def prod[A](a: Exp[MDArray[A]])(implicit ev: Numeric[A], mf: ClassManifest[A]): Exp[A] = reduce[A](ev.one, a, ev.times, "prod")
  def all(a: Exp[MDArray[Boolean]]): Exp[Boolean] = reduce[Boolean](true, a, (x:Boolean, y:Boolean) => x && y, "all")
  def any(a: Exp[MDArray[Boolean]]): Exp[Boolean] = reduce[Boolean](false, a, (x:Boolean, y:Boolean) => x || y, "any")
  def maxVal[A](a: Exp[MDArray[A]])(implicit ev: Ordering[A], mf: ClassManifest[A]): Exp[A] = reduce[A](sel(zeros(dim(a)),a), a, (a:A, b:A) => if (ev.gt(a, b)) a else b, "maxVal")
  def minVal[A](a: Exp[MDArray[A]])(implicit ev: Ordering[A], mf: ClassManifest[A]): Exp[A] = reduce[A](sel(zeros(dim(a)),a), a, (a:A, b:A) => if (ev.lt(a, b)) a else b, "minVal")

  // Basic operations on matrices - they appear as private here
  def op[A, B](a:Exp[MDArray[A]], b:Exp[MDArray[A]])(op: (A, A) => B, opName: String)(implicit mfA: ClassManifest[A], mfB: ClassManifest[B], ov1: Overloaded4): Exp[MDArray[B]] =
    InfixOpAA(a, b, op, opName)
  def op[A, B](a:Exp[MDArray[A]], b:Exp[A])(op: (A, A) => B, opName: String)(implicit mfA: ClassManifest[A], mfB: ClassManifest[B], ov2: Overloaded5): Exp[MDArray[B]] =
    InfixOpAE(a, b, op, opName)
  def uop[A, B](a:Exp[MDArray[A]])(op: A => B, opName: String)(implicit mfA: ClassManifest[A], mfB: ClassManifest[B]): Exp[MDArray[B]] =
    UnaryOp(a, op, opName)
  def reduce[A](z: Exp[A], a: Exp[MDArray[A]], op: (A, A) => A, opName: String)(implicit mfA: ClassManifest[A]): Exp[A] =
    Reduce(z, a, op, opName)

  // With-comprehensions
  def toWithNode[A: ClassManifest](p: (With, Exp[MDArray[Int]] => Exp[MDArray[A]])): Exp[MDArray[A]] = {
    val withObject: With = p._1
    val function: Exp[MDArray[Int]] => Exp[MDArray[A]] = p._2

    val iv: Exp[MDArray[Int]] = IndexVector(withObject._lb, withObject._lbStrict, withObject._ub, withObject._ubStrict, withObject._step, withObject._width)
    WithNode(iv, function(iv))
  }

  def genArrayWith[A: ClassManifest](l: List[Pair[With, Exp[MDArray[Int]]=> Exp[MDArray[A]]]], shp: Exp[MDArray[Int]]): Exp[MDArray[A]] = GenArrayWith(l.map(p => toWithNode(p)), shp)
  def modArrayWith[A: ClassManifest](l: List[Pair[With, Exp[MDArray[Int]]=> Exp[MDArray[A]]]], a: Exp[MDArray[A]]): Exp[MDArray[A]] = ModArrayWith(l.map(p => toWithNode(p)), a)
  def foldArrayWith[A: ClassManifest](w: With, foldFunction: (Exp[MDArray[A]], Exp[MDArray[A]]) => Exp[MDArray[A]], neutral: Exp[MDArray[A]], f: Exp[MDArray[Int]] => Exp[MDArray[A]]): Exp[MDArray[A]] = FoldArrayWith(toWithNode(Pair(w, f)), neutral, foldFunction)

  // ToString
  def doToString[A](a: Exp[MDArray[A]]) = a.toString()

  protected val nothing: Exp[MDArray[Int]] = Nothing

  protected def addLateAssertions(result: Any, assertions: List[Exp[_]]): Unit = result match {
    case rd: RichDef[_] => rd.addAssertions(assertions)
    case sym: Sym[_] => addLateAssertions(findDefinition(sym).get.rhs, assertions)
    case _ => throw new Exception("addDefinition: Not a symbol or a rich definition")
  }
}