package com.bawnorton.msp.reference.selector

import com.bawnorton.msp.MethodVariantCollector
import com.bawnorton.msp.util.MixinSquaredConstants
import com.bawnorton.msp.handlers.injectionPoint.TargetHandlerResolver
import com.bawnorton.msp.util.getPossiblePrefixes
import com.demonwav.mcdev.platform.mixin.reference.MixinReference
import com.demonwav.mcdev.platform.mixin.util.findSourceClass
import com.demonwav.mcdev.platform.mixin.util.findSourceElement
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.insideAnnotationAttribute
import com.demonwav.mcdev.util.reference.ReferenceResolver
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.util.parentOfType

abstract class AbstractTargetHandlerReference : ReferenceResolver(), MixinReference {
    override val description: String
        get() = "target reference '%s'"

    override fun isUnresolved(context: PsiElement): Boolean {
        return resolveNavigationTargets(context) == null
    }

    override fun isValidAnnotation(name: String, project: Project) = name == MixinSquaredConstants.TARGET_HANDLER

    abstract fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>?

    override fun resolveReference(context: PsiElement): PsiElement? {
        return resolveNavigationTargets(context)?.firstOrNull()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        return emptyArray()
    }
}

object TargetHandlerMixinReference : AbstractTargetHandlerReference() {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinSquaredConstants.TARGET_HANDLER, "mixin")

    override fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return null
        return target.findSourceClass(context.project, context.resolveScope, canDecompile = true)?.let { arrayOf(it) }
    }
}

object TargetHandlerNameReference : AbstractTargetHandlerReference(), MethodVariantCollector {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinSquaredConstants.TARGET_HANDLER, "name")

    override fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return null
        val targetMethods = resolver.resolveNameTargets(target) ?: return null
        return targetMethods.mapNotNull {
            it.findSourceElement(
                target,
                context.project,
                context.resolveScope,
                canDecompile = true
            )
        }.toTypedArray()
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

    override fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val prefix = targetHandler.findAttributeValue("prefix")?.constantStringValue ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val targetAnnotations = resolver.resolvePrefixTargets(prefix) ?: return null
        return targetAnnotations.toTypedArray()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return emptyArray()
        val prefix = targetHandler.findAttributeValue("prefix")?.constantStringValue ?: return emptyArray()
        return getPossiblePrefixes().filter { it.startsWith(prefix) }.toTypedArray()
    }
}