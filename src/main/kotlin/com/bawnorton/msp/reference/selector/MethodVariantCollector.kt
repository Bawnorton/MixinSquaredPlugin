/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2025 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.bawnorton.msp.reference.selector

import com.demonwav.mcdev.platform.mixin.reference.toMixinString
import com.demonwav.mcdev.platform.mixin.util.ClassAndMethodNode
import com.demonwav.mcdev.platform.mixin.util.findOrConstructSourceMethod
import com.demonwav.mcdev.platform.mixin.util.memberReference
import com.demonwav.mcdev.util.MemberReference
import com.demonwav.mcdev.util.reference.completeToLiteral
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

        methods.removeIf { it.name == "<init>" || it.name == "<clinit>" }

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

    private fun createLookup(
        context: PsiElement,
        nodes: Sequence<ClassAndMethodNode>,
        uniqueMethods: Set<String>,
    ): Array<Any> {
        return nodes
            .map { node ->
                val targetMethodInfo = if (!requireDescriptor && node.method.name in uniqueMethods) {
                    MemberReference(node.method.name)
                } else {
                    node.method.memberReference
                }

                val sourceMethod = node.method.findOrConstructSourceMethod(
                    node.clazz,
                    context.project,
                    scope = context.resolveScope,
                    canDecompile = false,
                )
                val builder = JavaLookupElementBuilder.forMethod(
                    sourceMethod,
                    targetMethodInfo.toMixinString(),
                    PsiSubstitutor.EMPTY,
                    null,
                ).withPresentableText(node.method.name)
                addCompletionInfo(builder, context, targetMethodInfo)
            }.toList().toTypedArray()
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