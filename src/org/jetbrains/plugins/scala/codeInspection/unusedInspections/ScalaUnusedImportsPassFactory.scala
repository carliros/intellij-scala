package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import java.lang.String
import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPassRegistrar, TextEditorHighlightingPass, TextEditorHighlightingPassFactory}

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */
class ScalaUnusedImportsPassFactory(highlightingPassRegistrar: TextEditorHighlightingPassRegistrar)
        extends TextEditorHighlightingPassFactory {
  highlightingPassRegistrar.registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL),
    null, false, -1)

  def projectClosed() {}

  def projectOpened() {}

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    new ScalaUnusedImportPass(file, editor)
  }

  def initComponent() {}

  def disposeComponent() {}

  def getComponentName: String = "Scala Unused import pass factory"
}