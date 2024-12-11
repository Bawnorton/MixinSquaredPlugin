package com.bawnorton.msp

import com.demonwav.mcdev.platform.mixin.reference.toMixinString
import com.demonwav.mcdev.platform.mixin.util.ClassAndMethodNode
import com.demonwav.mcdev.platform.mixin.util.findOrConstructSourceMethod
import com.demonwav.mcdev.platform.mixin.util.memberReference
import com.demonwav.mcdev.util.MemberReference
import com.demonwav.mcdev.util.reference.completeToLiteral
import com.demonwav.mcdev.util.toTypedArray
import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.util.ArrayUtil
import org.objectweb.asm.tree.ClassNode

interface MethodVariantCollector {
    fun collectVariants(context: PsiElement, target: ClassNode): Array<Any> {
        val methods = target.methods ?: return ArrayUtil.EMPTY_OBJECT_ARRAY

        // All methods which are not unique by their name need to be qualified with the descriptor
        val visitedMethods = HashSet<String>()
        val uniqueMethods = HashSet<String>()

        for (method in methods) {
            val name = method.name
            if (visitedMethods.add(name)) {
                uniqueMethods.add(name)
            } else {
                uniqueMethods.remove(name)
            }
        }

        return createLookup(context, methods.asSequence().map { ClassAndMethodNode(target, it) }, uniqueMethods)
    }

    fun collectVariants(context: PsiElement, targets: Collection<ClassNode>): Array<Any> {
        val groupedMethods = targets.asSequence()
            .flatMap { target ->
                target.methods?.asSequence()?.map { ClassAndMethodNode(target, it) } ?: emptySequence()
            }
            .groupBy { it.method.memberReference }
            .values

        // All methods which are not unique by their name need to be qualified with the descriptor
        val visitedMethods = HashSet<String>()
        val uniqueMethods = HashSet<String>()

        val allMethods = ArrayList<ClassAndMethodNode>(groupedMethods.size)

        for (methods in groupedMethods) {
            val firstMethod = methods.first()
            val name = firstMethod.method.name
            if (visitedMethods.add(name)) {
                uniqueMethods.add(name)
            } else {
                uniqueMethods.remove(name)
            }

            // If we have a method with the same name and descriptor in at least
            // as many classes as targets it should be present in all of them.
            // Not sure how you would have more methods than targets but who cares.
            if (methods.size >= targets.size) {
                allMethods.add(firstMethod)
            }
        }

        return createLookup(context, allMethods.asSequence(), uniqueMethods)
    }

    private fun createLookup(
        context: PsiElement,
        methods: Sequence<ClassAndMethodNode>,
        uniqueMethods: Set<String>,
    ): Array<Any> {
        return methods
            .map { m ->
                val targetMethodInfo = if (!requireDescriptor && m.method.name in uniqueMethods) {
                    MemberReference(m.method.name)
                } else {
                    m.method.memberReference
                }

                val sourceMethod = m.method.findOrConstructSourceMethod(
                    m.clazz,
                    context.project,
                    scope = context.resolveScope,
                    canDecompile = false,
                )
                val builder = JavaLookupElementBuilder.forMethod(
                    sourceMethod,
                    targetMethodInfo.toMixinString(),
                    PsiSubstitutor.EMPTY,
                    null,
                )
                    .withPresentableText(m.method.name)
                addCompletionInfo(builder, context, targetMethodInfo)
            }.toTypedArray()
    }

    val requireDescriptor: Boolean

    fun addCompletionInfo(
        builder: LookupElementBuilder,
        context: PsiElement,
        targetMethodInfo: MemberReference,
    ): LookupElementBuilder {
        return builder.completeToLiteral(context)
    }
}