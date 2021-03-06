package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScMember}
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import Utils._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiManager, ScalaPsiElementFactory}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType
import scala.Some

/**
 * Nikolay.Tropin
 * 5/21/13
 */
class Simplification(val replacementText: String, val hint: String, val rangeInParent: TextRange)

/*
 * After defining new simplification type one needs to add it to the
 * OperationOnCollectionInspection.possibleSimplificationTypes
 * */
abstract class SimplificationType(inspection: OperationOnCollectionInspection) {
  def key: String
  def hint: String
  def description: String
  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification]

  val likeOptionClasses = inspection.getLikeOptionClasses
  val likeCollectionClasses = inspection.getLikeCollectionClasses

  def createSimplification(methodToBuildFrom: MethodRepr,
                           parentExpr: ScExpression,
                           argText: String,
                           newMethodName: String,
                           hint: String): List[Simplification] = {
    val rangeInParent = methodToBuildFrom.rightRangeInParent(parentExpr)
    methodToBuildFrom.itself match {
      case ScInfixExpr(left, _, right) =>
        List(new Simplification(left.getText + s" $newMethodName " + argText, hint, rangeInParent))
      case _: ScMethodCall =>
        methodToBuildFrom.optionalBase match {
          case Some(baseExpr) =>
            val baseText = baseExpr.getText
            //val bracedArg = if (argText != "") s"($argText)" else ""
            val bracedArg = methodToBuildFrom.args match {
              case Seq(_: ScBlock) => argText
              case Seq(_: ScParenthesisedExpr) => argText
              case _ if argText == "" => ""
              case _ => s"($argText)"
            }
            List(new Simplification(baseText + "." + newMethodName + bracedArg, hint, rangeInParent))
          case _ => Nil
        }
      case _ => Nil
    }
  }
}

class MapGetOrElseFalse(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  def key: String = "map.getOrElse.false"
  def hint = InspectionBundle.message("map.getOrElse.false.hint")
  def description = hint
  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    val (lastArgs, secondArgs) = (last.args, second.args)
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) =>
        if (lastRef.refName == "getOrElse" &&
                secondRef.refName == "map" &&
                isLiteral(lastArgs, text = "false") &&
                secondArgs.size == 1 &&
                isFunctionWithBooleanReturn(secondArgs(0)) &&
                checkResolve(lastRef, likeOptionClasses) &&
                checkResolve(secondRef, likeOptionClasses))
        {
          createSimplification(second, last.itself, second.args(0), "exists", hint)
        }
        else Nil
      case _ => Nil
    }
  }
}

class FindIsDefined(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  def key: String = "find.isDefined"
  def hint = InspectionBundle.message("find.isDefined.hint")
  def description = hint

  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) =>
        if (lastRef.refName == "isDefined" &&
                secondRef.refName == "find" &&
                checkResolve(lastRef, likeOptionClasses) &&
                checkResolve(secondRef, likeCollectionClasses)) {
          createSimplification(second, last.itself, second.args(0), "exists", hint)
        }
        else Nil
      case _ => Nil
    }
  }
}

class FindNotEqualsNone(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){

  def key: String = "find.notEquals.none"
  def hint = InspectionBundle.message("find.notEquals.none.hint")
  def description = hint

  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    val lastArgs = last.args
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) =>
        if (lastRef.refName == "!=" &&
                secondRef.refName == "find" &&
                lastArgs.size == 1 &&
                lastArgs(0).getText == "None" &&
                checkResolve(lastArgs(0), Array("scala.None")) &&
                checkResolve(secondRef, likeCollectionClasses)) {
          createSimplification(second, last.itself, second.args(0), "exists", hint)
        } else Nil
      case _ => Nil
    }
  }
}

class FilterHeadOption(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {

  def key: String = "filter.headOption"
  def hint = InspectionBundle.message("filter.headOption.hint")
  def description = hint

  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) =>
        if (lastRef.refName == "headOption" &&
                secondRef.refName == "filter" &&
                checkResolve(lastRef, likeCollectionClasses) &&
                checkResolve(secondRef, likeCollectionClasses))
        {
          createSimplification(second, last.itself, second.args(0), "find", hint)
        }
        else Nil
      case _ => Nil
    }
  }
}

class FoldLeftSum(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){
  def key: String = "foldLeft.sum"
  def hint = InspectionBundle.message("foldLeft.sum.hint")
  def description = hint

  private def checkNumeric(optionalBase: Option[ScExpression]): Boolean = {
    optionalBase match {
      case Some(expr) =>
        expr.getType(TypingContext.empty).getOrAny match {
          case ScParameterizedType(_, Seq(scType)) =>
            val project = expr.getProject
            val manager = ScalaPsiManager.instance(project)
            val stringClass = manager.getCachedClass(GlobalSearchScope.allScope(project), "java.lang.String")
            val stringType = new ScDesignatorType(stringClass)
            if (scType.conforms(stringType)) false
            else {
              val exprWithSum = ScalaPsiElementFactory.createExpressionFromText(expr.getText + ".sum", expr.getContext).asInstanceOf[ScExpression]
              exprWithSum.findImplicitParameters match {
                case Some(implPar) => true
                case _ => false
              }
            }
          case _ => false
        }
      case _ => false
    }
  }
  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (None, Some(secondRef)) =>
        if (List("foldLeft","/:").contains(secondRef.refName) &&
                isLiteral(second.args, "0") &&
                last.args.size == 1 &&
                isSum(last.args(0)) &&
                checkResolve(secondRef, likeCollectionClasses) &&
                checkNumeric(second.optionalBase))
        {
          createSimplification(second, last.itself, "", "sum", hint)
        }
        else Nil
      case _ => Nil
    }
  }
}

class FoldLeftTrueAnd(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){

  def key: String = "foldLeft.true.and"
  def hint = InspectionBundle.message(key + ".hint")
  def description = hint

  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (None, Some(secondRef)) =>
        if (List("foldLeft","/:").contains(secondRef.refName) &&
                isLiteral(second.args, "true") &&
                last.args.size == 1 &&
                checkResolve(secondRef, likeCollectionClasses))
        {
          val funcArg = andWithSomeFunction(last.args(0))
          if (funcArg.isDefined)
            createSimplification(second, last.itself, funcArg.get, "forall", hint)
          else Nil
        }
        else Nil
      case _ => Nil
    }
  }
}

class FilterSize(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {

  def key: String = "filter.size"
  def hint = InspectionBundle.message("filter.size.hint")
  def description = hint

  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) =>
        if (List("size", "length").contains(lastRef.refName) &&
                secondRef.refName == "filter" &&
                checkResolve(lastRef, likeCollectionClasses) &&
                checkResolve(secondRef, likeCollectionClasses))
        {
          createSimplification(second, last.itself, second.args(0), "count", hint)
        }
        else Nil
      case _ => Nil
    }
  }
}
