package com.bawnorton.msp.inspection

import com.bawnorton.msp.util.MixinSquaredConstants
import com.demonwav.mcdev.platform.mixin.inspection.MixinInspection
import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.constantValue
import com.demonwav.mcdev.util.findQualifiedClass
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope

class TargetHandlerTooEarlyInspection : MixinInspection() {
    override fun getStaticDescription() = "@TargetHandler must target a Mixin that applies before the enclosing Mixin"

    override fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor = Visitor(holder)

    private class Visitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        private var currentPriority: Int? = null
        private var currentMixin: PsiAnnotation? = null

        override fun visitAnnotation(annotation: PsiAnnotation) {
            if (annotation.qualifiedName == MixinConstants.Annotations.MIXIN) {
                currentMixin = annotation
                currentPriority = annotation.findDeclaredAttributeValue("priority")?.constantValue as? Int ?: 1000
                return
            }
            if (annotation.qualifiedName != MixinSquaredConstants.TARGET_HANDLER) {
                return
            }

            val targetMixin = annotation.findDeclaredAttributeValue("mixin") ?: return
            val targetMixinValue = targetMixin.constantStringValue ?: return
            val targetMixinClass = findQualifiedClass(annotation.project, targetMixinValue, GlobalSearchScope.allScope(annotation.project)) ?: return
            val targetMixinMixinAnnotation = targetMixinClass.modifierList?.findAnnotation(MixinConstants.Annotations.MIXIN) ?: return
            val targetMixinPriority = targetMixinMixinAnnotation.findDeclaredAttributeValue("priority")?.constantValue as? Int ?: 1000

            if (currentPriority != null && currentPriority!! <= targetMixinPriority) {
                holder.registerProblem(
                    targetMixin,
                    "@TargetHandler must target a Mixin that applies before the enclosing Mixin",
                    Fix(currentMixin!!, targetMixinPriority + 500)
                )
            }
        }
    }

    class Fix(element: PsiAnnotation, private val newPriority: Int) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
        override fun getFamilyName() = "Change mixin priority"

        override fun getText() = "Change mixin priority to $newPriority"

        override fun invoke(
            project: Project,
            file: PsiFile,
            editor: Editor?,
            startElement: PsiElement,
            endElement: PsiElement
        ) {
            val annotation = startElement as PsiAnnotation
            val newValue = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newPriority.toString(), annotation)
            annotation.setDeclaredAttributeValue("priority", newValue)
        }
    }
}