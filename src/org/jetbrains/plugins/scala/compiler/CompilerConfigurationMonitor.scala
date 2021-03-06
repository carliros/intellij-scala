package org.jetbrains.plugins.scala
package compiler

import config.ScalaFacet
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.facet.{ProjectWideFacetListenersRegistry, ProjectWideFacetAdapter}
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompileContext, CompileTask, CompilerManager}

/**
 * @author Pavel Fatin
 */
class CompilerConfigurationMonitor(project: Project) extends ProjectComponent {
  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    def execute(context: CompileContext): Boolean = {
      if (isScalaProject && isCompileServerEnabled && isAutomakeEnabled) {
        val message = "Automake is not supported with Scala compile server. " +
                "Please either disable the compile server or turn off \"Project Setings / Compiler / Make project automatically\"."
        context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1)
        false
      } else {
        true
      }
    }
  })

  private def registry = ProjectWideFacetListenersRegistry.getInstance(project)

  private def compilerConfiguration = CompilerWorkspaceConfiguration.getInstance(project)

  private def compileServerConfiguration = ScalaApplicationSettings.getInstance

  private def isScalaProject = ScalacBackendCompiler.isScalaProject(ModuleManager.getInstance(project).getModules)

  private def isCompileServerEnabled = compileServerConfiguration.COMPILE_SERVER_ENABLED

  private def isAutomakeEnabled = compilerConfiguration.MAKE_PROJECT_ON_SAVE

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
    registry.registerListener(ScalaFacet.Id, FacetListener)

    if (isScalaProject && isCompileServerEnabled) {
      disableAutomake()
    }
  }

  def projectClosed() {
    registry.unregisterListener(ScalaFacet.Id, FacetListener)
  }

  private def disableAutomake() {
    compilerConfiguration.MAKE_PROJECT_ON_SAVE = false
  }

  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      if (isCompileServerEnabled) {
        disableAutomake()
      }
    }
  }
}