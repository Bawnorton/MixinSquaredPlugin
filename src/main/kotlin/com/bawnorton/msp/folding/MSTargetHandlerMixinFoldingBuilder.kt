package com.bawnorton.msp.folding


import com.bawnorton.msp.util.MixinSquaredConstants
import com.demonwav.mcdev.platform.mixin.MixinModuleType
import com.demonwav.mcdev.platform.mixin.reference.target.TargetReference
import com.demonwav.mcdev.util.createLiteralExpression
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.tree.util.children
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase.SHOW_CONTAINING_CLASS
import com.intellij.psi.util.PsiFormatUtilBase.SHOW_NAME
import com.intellij.psi.util.PsiFormatUtilBase.SHOW_PARAMETERS
import com.intellij.psi.util.PsiFormatUtilBase.SHOW_TYPE
import com.intellij.psi.util.PsiTreeUtil

class MSTargetHandlerMixinFoldingBuilder : CustomFoldingBuilder() {

    override fun isDumbAware(): Boolean = false

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean =
        MSFoldingSettings.instance.state.foldTargetHandler

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String? {
        val key = node.treeParent.firstChildNode?.text ?: return null
        val element = node.psi
        when (key) {
            "mixin" -> {
                val text = element.text
                return text.substringAfterLast('.').substringBeforeLast('"')
            }
            "name" -> {
                val mixinOwner = findMixinInAST(node) ?: return null
                val ownerAttachedPsiElement = JavaPsiFacade.getElementFactory(element.project)
                    .createLiteralExpression(
                        "L${mixinOwner.replace('.', '/')};" +
                                element.text.substringAfter("\"").substringBeforeLast("\"")
                    )
                return TargetReference.resolveTarget(ownerAttachedPsiElement)?.let { formatElement(it) }
            }
            else -> return null
        }
    }

    private fun findMixinInAST(node: ASTNode): String? {
        val annotationParamList = node.treeParent?.treeParent ?: return null
        annotationParamList.children().iterator().forEach {
            child -> (child.psi as? PsiNameValuePair)?.let {
                val id = it.nameIdentifier?.text ?: return@let
                if(id == "mixin") {
                    val text = it.value?.text ?: return@let
                    return text.substringAfter("\"").substringBeforeLast("\"")
                }
            }
        }
        return null
    }

    private fun formatElement(element: PsiElement): String? {
        return when (element) {
            is PsiMethod -> {
                val options = SHOW_NAME or SHOW_PARAMETERS or SHOW_CONTAINING_CLASS
                PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY, options, SHOW_TYPE)
            }
            is PsiVariable -> {
                val options = SHOW_NAME or SHOW_CONTAINING_CLASS
                PsiFormatUtil.formatVariable(element, options, PsiSubstitutor.EMPTY)
            }
            is NavigationItem -> element.presentation?.presentableText
            else -> null
        }
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

            if (!settings.foldTargetHandler) {
                return
            }

            val qualifiedName = annotation.qualifiedName ?: return
            if (qualifiedName != MixinSquaredConstants.TARGET_HANDLER) {
                return
            }

            listOf("mixin", "name").forEach {
                val attribute = annotation.findDeclaredAttributeValue(it) ?: return@forEach
                descriptors.add(FoldingDescriptor(attribute, attribute.textRange))
            }
        }
    }
}