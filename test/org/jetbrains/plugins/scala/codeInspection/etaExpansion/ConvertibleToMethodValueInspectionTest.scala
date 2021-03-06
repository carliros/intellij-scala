package org.jetbrains.plugins.scala
package codeInspection.etaExpansion

import org.jetbrains.plugins.scala.codeInspection.{ScalaLightInspectionFixtureTestAdapter, InspectionBundle}
import com.intellij.codeInspection.LocalInspectionTool

/**
 * Nikolay.Tropin
 * 6/3/13
 */
class ConvertibleToMethodValueInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  val annotation = InspectionBundle.message("convertible.to.method.value.name")
  val hintAnon = InspectionBundle.message("convertible.to.method.value.anonymous.hint")
  val hintEta = InspectionBundle.message("convertible.to.method.value.eta.hint")

  protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ConvertibleToMethodValueInspection]

  def test_methodCallUntyped() {
    val selected = s"""object A {
                     |  def f(x: Int, y: Int) {
                     |  }
                     |  val f1 = ${START}A.f(_, _)$END
                     |}
                     |""".stripMargin
    check(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1 = A.f(_, _)
                 |}""".stripMargin
    val result = """object A {
                   |  def f(x: Int, y: Int) {
                   |  }
                   |  val f1 = A.f _
                   |}""".stripMargin
    testFix(text, result, hintAnon)
  }

  def test_infixUntyped() {
    val text = """object A {
                     |  def f(x: Int, y: Int) {
                     |  }
                     |  val f1 = A f (_, _)
                     |}
                     |""".stripMargin
    checkHasNoErrors(text)
  }

  def test_methodCallEtaUntyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1 = A.f _
                 |}
                 |""".stripMargin
    checkHasNoErrors(text)
  }

  def test_methodCallTyped() {
    val selected = s"""object A {
                     |  def f(x: Int, y: Int) {
                     |  }
                     |  val f1: (Int, Int) => Unit = ${START}A.f(_, _)$END
                     |}""".stripMargin
    check(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1: (Int, Int) => Unit = A.f(_, _)
                 |}""".stripMargin
    val result = """object A {
                   |  def f(x: Int, y: Int) {
                   |  }
                   |  val f1: (Int, Int) => Unit = A.f
                   |}""".stripMargin
    testFix(text, result, hintAnon)
  }

  def test_methodCallEtaTyped() {
    val selected = s"""object A {
                     |  def f(x: Int, y: Int) {
                     |  }
                     |  val f1: (Int, Int) => Unit = ${START}A.f _$END
                     |}""".stripMargin
    check(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1: (Int, Int) => Unit = A.f _
                 |}""".stripMargin
    val result = """object A {
                   |  def f(x: Int, y: Int) {
                   |  }
                   |  val f1: (Int, Int) => Unit = A.f
                   |}""".stripMargin
    testFix(text, result, hintEta)
  }

  def test_methodCallWithDefaultUntyped() {
    val selected = s"""object A {
                     |  def f(x: Int, y: Int = 0) {
                     |  }
                     |  val f1 = ${START}A.f(_, _)$END
                     |}""".stripMargin
    check(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int = 0) {
                 |  }
                 |  val f1 = A.f(_, _)
                 |}""".stripMargin
    val result = """object A {
                   |  def f(x: Int, y: Int = 0) {
                   |  }
                   |  val f1 = A.f _
                   |}""".stripMargin
    testFix(text, result, hintAnon)
  }

  def test_methodCallWithDefaultTyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int = 0) {
                 |  }
                 |  val f1: (Int) => Unit = A.f(_)
                 |}
                 |""".stripMargin
    checkHasNoErrors(text)
  }

  def test_infixWithDefaultTyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int = 0) {
                 |  }
                 |  val f1: (Int) => Unit = A f _
                 |}
                 |""".stripMargin
    checkHasNoErrors(text)
  }

  def test_methodCallTypedArgs() {
    val text = """object A {
                 |  def f(x: Any, y: Int = 0) {
                 |  }
                 |  val f1 = A.f(_: Int, _)
                 |}""".stripMargin
    checkHasNoErrors(text)
  }

  def test_infixTypedArgs() {
    val text = """object A {
                 |  def f(x: Any, y: Int = 0) {
                 |  }
                 |  val f1 = A f (_: Int, _: Int)
                 |}""".stripMargin
    checkHasNoErrors(text)
  }
}
