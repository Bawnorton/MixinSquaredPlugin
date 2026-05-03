package com.bawnorton.msp.completion

import com.bawnorton.msp.util.MixinSquaredConstants
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.SkipAutopopupInStrings
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

class MSCompletionConfidence : CompletionConfidence() {
    private val msAnnotation = PlatformPatterns.psiElement()
        .inside(
            false,
            PsiJavaPatterns.psiAnnotation().qName(
                StandardPatterns.string().startsWith(MixinSquaredConstants.PACKAGE)
            ),
            PlatformPatterns.psiFile(),
        )!!

    override fun shouldSkipAutopopup(
        editor: Editor,
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int
    ): ThreeState {
        return if (SkipAutopopupInStrings.isInStringLiteral(contextElement) && msAnnotation.accepts(contextElement)) {
            ThreeState.NO
        } else {
            ThreeState.UNSURE
        }
    }
}