package com.bawnorton.msp.folding


import com.bawnorton.msp.util.MixinSquaredConstants
import com.demonwav.mcdev.platform.mixin.MixinModuleType
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile

class MSTargetHandlerMixinFoldingBuilder : CustomFoldingBuilder() {

    override fun isDumbAware(): Boolean = false

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean =
        MSFoldingSettings.instance.state.foldTargetHandlerMixin

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
        val element = node.psi
        return element.text.substringAfterLast('.')
    }

    override fun buildLanguageFoldRegions(
        descriptors: MutableList<FoldingDescriptor>,
        root: PsiElement,
        document: Document,
        quick: Boolean
    ) {
        if (root !is PsiJavaFile || !MixinModuleType.isInModule(root)) {
            return
        }

        root.accept(Visitor(descriptors))
    }

    private class Visitor(private val descriptors: MutableList<FoldingDescriptor>) :
        JavaRecursiveElementWalkingVisitor() {
        val settings = MSFoldingSettings.instance.state

        override fun visitAnnotation(annotation: PsiAnnotation) {
            super.visitAnnotation(annotation)

            if (!settings.foldTargetHandlerMixin) {
                return
            }

            val qualifiedName = annotation.qualifiedName ?: return
            if (qualifiedName != MixinSquaredConstants.TARGET_HANDLER) {
                return
            }

            val mixin = annotation.findDeclaredAttributeValue("mixin") ?: return
            descriptors.add(FoldingDescriptor(mixin, mixin.textRange))
        }
    }
}