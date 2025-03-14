package com.bawnorton.msp.reference.selector

import com.bawnorton.msp.util.MixinSquaredConstants
import com.bawnorton.msp.handlers.TargetHandlerResolver
import com.bawnorton.msp.util.getPossiblePrefixes
import com.demonwav.mcdev.platform.mixin.reference.MixinReference
import com.demonwav.mcdev.platform.mixin.util.findSourceClass
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.insideAnnotationAttribute
import com.demonwav.mcdev.util.reference.PolyReferenceResolver
import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiLiteral
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.parentOfType

abstract class AbstractTargetHandlerReference : PolyReferenceResolver(), MixinReference {
    override val description: String = "target reference '%s'"

    override fun isUnresolved(context: PsiElement): Boolean {
        return resolve(context) == null
    }

    override fun isValidAnnotation(name: String, project: Project) = name == MixinSquaredConstants.TARGET_HANDLER

    abstract fun resolve(context: PsiElement): List<PsiElement>?

    fun resolveForNavigation(context: PsiElement): Array<PsiElement>? {
        return resolve(context)?.toTypedArray()
    }

    override fun resolveReference(context: PsiElement): Array<ResolveResult> {
        return resolve(context)?.map { PsiElementResolveResult(it) }?.toTypedArray() ?: ResolveResult.EMPTY_ARRAY
    }
}

object TargetHandlerMixinReference : AbstractTargetHandlerReference() {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinSquaredConstants.TARGET_HANDLER, "mixin")

    override fun resolve(context: PsiElement): List<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return null
        return target.findSourceClass(context.project, context.resolveScope, canDecompile = true)?.let { listOf(it) }
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        return emptyArray()
    }
}

object TargetHandlerNameReference : AbstractTargetHandlerReference(), MethodVariantCollector {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinSquaredConstants.TARGET_HANDLER, "name")

    override fun resolve(context: PsiElement): List<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        return resolver.resolveNameTargets()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return emptyArray()
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return emptyArray()
        return collectVariants(context, target)
    }

    override val requireDescriptor = false
}

object TargetHandlerPrefixReference : AbstractTargetHandlerReference() {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinSquaredConstants.TARGET_HANDLER, "prefix")

    override fun resolve(context: PsiElement): List<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val prefix = targetHandler.findAttributeValue("prefix")?.constantStringValue ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        return resolver.resolvePrefixTargets(prefix)
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        return getPossiblePrefixes().toTypedArray()
    }
}