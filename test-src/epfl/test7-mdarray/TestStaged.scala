package scala.virtualization.lms
package epfl
package test7

import test7.original.MDArray
import internal.GraphVizExport
import test2.{DisableDCE, DisableCSE}
import common._
import test4.ScalaGenFunctionsExternal
import test1.ScalaGenArith
import java.io.{FileWriter, PrintWriter}

/*
To run only this test use:
sbt 'test-only scala.virtualization.lms.epfl.test7.TestStagedPDE1Benchmark'
*/
class TestStaged extends FileDiffSuite {

  val prefix = "test-out/epfl/test7-staged-"
  var typeFunction: Int => String = (i:Int) => ""

  def testPower = {
    // Perform actual tests:
    val pde1 = new PDE1BenchmarkStaged with MDArrayBaseExp with IfThenElseExp
    val gol = new GameOfLifeStaged with MDArrayBaseExp with IfThenElseExp
    val scp = new ScopeTestStaged with MDArrayBaseExp with IfThenElse

    // TODO: Re-enable scope test when scopes are enabled
    //performExperiment(scp, scp.testStaged(scp.knownOnlyAtRuntime[Boolean]("a"), scp.knownOnlyAtRuntime[Int]("b")), prefix + "scope-test")
    performExperiment(scp, scp.testShapes(scp.knownOnlyAtRuntime[Int]("a"), scp.knownOnlyAtRuntime[Int]("b")), prefix + "shape-test")

    // PDE1 experiments
    performExperiment(pde1, pde1.range1(pde1.knownOnlyAtRuntime[Double]("matrix1"), 1), prefix + "range1-test")
    performExperiment(pde1, pde1.range2(pde1.knownOnlyAtRuntime[Double]("matrix2"), 1), prefix + "range2-test")
    performExperiment(pde1, pde1.range3(pde1.knownOnlyAtRuntime[Double]("matrix3"), 1), prefix + "range3-test")
    // TODO: Include ranges to make this work
    //performExperiment(pde1, pde1.range4(pde1.knownOnlyAtRuntime[Double]("matrix4"), 1), prefix + "range4-test")
    performExperiment(pde1, pde1.range5(pde1.knownOnlyAtRuntime[Double]("matrix5"), 1), prefix + "range5-test")

    // Game of Life experiments
    performExperiment(gol, gol.reshape(gol.convertFromListRep(10::10::Nil), gol.knownOnlyAtRuntime[Int]("input")), prefix + "reshape")
    performExperiment(gol, gol.gameOfLife(gol.knownOnlyAtRuntime[Int]("input")), prefix + "game-of-life-generic")
    performExperiment(gol, gol.gameOfLife(gol.reshape(gol.convertFromListRep(10::10::Nil), gol.knownOnlyAtRuntime[Int]("input"))), prefix + "game-of-life-10-by-10")
  }

  def performExperiment(pde1: MDArrayBaseExp with IfThenElseExp, expr: Any, fileName: String) {

    val typing = new MDArrayTypingBubbleUp { val IR: pde1.type = pde1 }

    System.err.println("Performing experiment: " + fileName)

    withOutFile(fileName + "-type-inference") {
      try {
        typing.doTyping(expr.asInstanceOf[pde1.Exp[_]], true)
        val export = new MDArrayGraphExport {
          val IR: pde1.type = pde1
          override val TY = typing
        }
        export.emitDepGraph(expr.asInstanceOf[pde1.Exp[_]], new PrintWriter(fileName + "-dot"), false)
      } catch {
        case e => throw e
      }

      //

      // Generate the corresponding code :)
      implicit val printWriter: PrintWriter = IndentWriter.getIndentPrintWriter(new FileWriter(fileName + "-code.scala"))
      val scalaGen = new ScalaGenMDArray with ScalaGenIfThenElse { val IR: pde1.type = pde1; override val TY = typing }
      expr match {
        case e: pde1.Exp[_] => scalaGen.emitSource(e, "Experiment")
        case _ => printWriter.println("cannot generate code")
      }
      printWriter.close
    }
  }
}