package com.bawnorton.msp.reference.selector

import com.bawnorton.msp.handlers.TargetHandlerResolver
import com.bawnorton.msp.util.MixinSquaredConstants
import com.bawnorton.msp.util.getPossiblePrefixes
import com.demonwav.mcdev.platform.mixin.reference.MixinReference
import com.demonwav.mcdev.platform.mixin.util.findSourceClass
import com.demonwav.mcdev.platform.mixin.util.mixinTargets
import com.demonwav.mcdev.util.*
import com.demonwav.mcdev.util.reference.PolyReferenceResolver
import com.demonwav.mcdev.util.reference.completeToLiteral
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
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
        val project = context.project
        val scope = context.resolveScope

        val enclosingClass = context.parentOfType<PsiClass>() ?: return emptyArray()
        val mixinTargets = enclosingClass.mixinTargets.map { it.name.replace("/", ".") }

        val allMixinAnnotations = JavaAnnotationIndex.getInstance().getAnnotations("Mixin", project, scope)
        val ownerToTargets = allMixinAnnotations.map { mixinAnnotation ->
            val owningMixin = mixinAnnotation.parentOfType<PsiClass>()
            val literalClassTargets = mixinAnnotation.findDeclaredAttributeValue(null)?.resolveClassArray()?.mapNotNull { it.qualifiedName }
            val stringClassTargets = mixinAnnotation.findDeclaredAttributeValue("targets")?.computeStringArray()
            val targets = (literalClassTargets ?: emptyList()) + (stringClassTargets ?: emptyList())
            owningMixin to targets
        }.filter { it.first != null }

        return ownerToTargets.filter { it.second.intersect(mixinTargets.toSet()).isNotEmpty() }
            .map { it.first?.qualifiedName!! }
            .filter { it != enclosingClass.qualifiedName }
            .map { LookupElementBuilder.create(it).completeMixin(context) }
            .toTypedArray()
    }

    private fun LookupElementBuilder.completeMixin(context: PsiElement): LookupElementBuilder {
        return completeToLiteral(context) { editor, reference ->
            val modifierList = reference.findContainingModifierList() ?: return@completeToLiteral
            val annotation = modifierList.findAnnotation(MixinSquaredConstants.TARGET_HANDLER) ?: return@completeToLiteral

            val project = reference.project
            val elementFactory = JavaPsiFacade.getElementFactory(project)
            val emptyExpr = elementFactory.createExpressionFromText("\"\"", null)
            annotation.setDeclaredAttributeValue("name", emptyExpr)
            val formattedAnnotation = CodeStyleManager.getInstance(project).reformat(annotation) as PsiAnnotation

            val targetHandlerLiteral = formattedAnnotation.findDeclaredAttributeValue("name") ?: return@completeToLiteral
            editor.caretModel.moveToOffset(targetHandlerLiteral.textRange.startOffset + 1)
        }
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