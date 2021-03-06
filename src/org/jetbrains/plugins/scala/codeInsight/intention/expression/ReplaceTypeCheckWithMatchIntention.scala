package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import codeInspection.typeChecking.TypeCheckToMatchUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMatchStmt, ScIfStmt, ScGenericCall}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.refactoring.rename.GroupInplaceRenamer
import extensions.inWriteAction


/**
 * Nikolay.Tropin
 * 5/16/13
 */

object ReplaceTypeCheckWithMatchIntention {
  def familyName = "Replace type check with pattern matching"
}

class ReplaceTypeCheckWithMatchIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = ReplaceTypeCheckWithMatchIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    for {
      iioCall <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScGenericCall], false)).filter(isIsInstOfCall)
      ifStmt <- Option(PsiTreeUtil.getParentOfType(iioCall, classOf[ScIfStmt]))
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition, onlyFirst = false) contains iioCall
    } {
      val offset = editor.getCaretModel.getOffset
      if (offset >= iioCall.getTextRange.getStartOffset && offset <= iioCall.getTextRange.getEndOffset)
        return true
    }
    false
  }

  def invoke(project: Project, editor: Editor, element: PsiElement) {
    for {
      iioCall <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScGenericCall], false)).filter(isIsInstOfCall)
      ifStmt <- Option(PsiTreeUtil.getParentOfType(iioCall, classOf[ScIfStmt]))
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition, onlyFirst = false) contains iioCall
    } {
      val (matchStmtOption, renameData) = buildMatchStmt(ifStmt, iioCall, onlyFirst = false)
      for (matchStmt <- matchStmtOption) {
        val newMatch = inWriteAction {
          ifStmt.replaceExpression(matchStmt, removeParenthesis = true).asInstanceOf[ScMatchStmt]
        }
        if (!ApplicationManager.getApplication.isUnitTestMode) {
          val renamer = new GroupInplaceRenamer(newMatch)
          setElementsForRename(newMatch, renamer, renameData)
          renamer.startRenaming()
        }
      }
    }
  }
}
