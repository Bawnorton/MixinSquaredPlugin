package com.bawnorton.msp.reference.selector

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.util.parentOfType

class TargetHandlerGTDHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        val stringLiteral = sourceElement.parentOfType<PsiLiteral>() ?: return null
        if (TargetHandlerMixinReference.ELEMENT_PATTERN.accepts(stringLiteral)) {
            return TargetHandlerMixinReference.resolveForNavigation(stringLiteral)
        }
        if (TargetHandlerNameReference.ELEMENT_PATTERN.accepts(stringLiteral)) {
            return TargetHandlerNameReference.resolveForNavigation(stringLiteral)
        }
        if (TargetHandlerPrefixReference.ELEMENT_PATTERN.accepts(stringLiteral)) {
            return TargetHandlerPrefixReference.resolveForNavigation(stringLiteral)
        }
        return null
    }
}