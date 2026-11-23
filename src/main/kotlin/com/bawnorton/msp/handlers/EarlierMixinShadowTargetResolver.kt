package com.bawnorton.msp.handlers

import com.demonwav.mcdev.platform.mixin.handlers.ShadowTargetResolver
import com.demonwav.mcdev.platform.mixin.util.FieldTargetMember
import com.demonwav.mcdev.platform.mixin.util.MethodTargetMember
import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import com.demonwav.mcdev.platform.mixin.util.MixinTargetMember
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.findFieldByName
import com.demonwav.mcdev.platform.mixin.util.mixinAnnotation
import com.demonwav.mcdev.platform.mixin.util.mixinTargets
import com.demonwav.mcdev.util.constantValue
import com.demonwav.mcdev.util.descriptor
import com.demonwav.mcdev.util.internalName
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
import com.intellij.psi.util.parentOfType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class EarlierMixinShadowTargetResolver : ShadowTargetResolver {
    override fun resolveTarget(
        shadow: PsiAnnotation,
        targetClass: ClassNode,
        member: PsiMember,
        effectiveName: String,
    ): List<MixinTargetMember> {
        val descriptor = when (member) {
            is PsiField -> member.descriptor
            is PsiMethod -> member.descriptor
            else -> null
        } ?: return emptyList()
        val currentMixin = member.containingClass ?: return emptyList()

        return earlierMixins(currentMixin)
            .filter { mixin -> mixin.mixinTargets.any { it.name == targetClass.name } }
            .mapNotNull { mixin ->
                val candidate = when (member) {
                    is PsiField -> mixin.findFieldByName(effectiveName, false)
                    is PsiMethod -> mixin.findMethodsByName(effectiveName, false)
                        .firstOrNull { it.descriptor == descriptor }
                    else -> null
                } ?: return@mapNotNull null
                if (candidate.memberDescriptor != descriptor || candidate.isShadow) return@mapNotNull null
                candidate.toTargetMember(mixin)
            }
            .toList()
    }

    override fun findCompletionTargets(mixinClass: PsiClass): Sequence<MixinTargetMember> {
        val targetNames = mixinClass.mixinTargets.mapTo(mutableSetOf()) { it.name }
        if (targetNames.isEmpty()) return emptySequence()

        return earlierMixins(mixinClass)
            .filter { mixin -> mixin.mixinTargets.mapTo(mutableSetOf()) { it.name }.containsAll(targetNames) }
            .flatMap { mixin ->
                (mixin.fields.asSequence() + mixin.methods.asSequence())
                    .filter { !it.isShadow && (it !is PsiMethod || !it.isConstructor) }
                    .filter { candidate ->
                        when (candidate) {
                            is PsiField -> mixinClass.findFieldByName(candidate.name, false) == null
                            is PsiMethod -> mixinClass.findMethodsByName(candidate.name, false)
                                .none { it.descriptor == candidate.descriptor }
                            else -> false
                        }
                    }
                    .mapNotNull { it.toTargetMember(mixin) }
            }
            .distinctBy { target ->
                when (target) {
                    is FieldTargetMember -> "field:${target.name}:${target.classAndField.field.desc}"
                    is MethodTargetMember -> "method:${target.name}:${target.classAndMethod.method.desc}"
                }
            }
    }

    private fun earlierMixins(currentMixin: PsiClass): Sequence<PsiClass> {
        val currentPriority = currentMixin.mixinAnnotation?.priority ?: return emptySequence()
        return JavaAnnotationIndex.getInstance()
            .getAnnotations("Mixin", currentMixin.project, currentMixin.resolveScope)
            .asSequence()
            .mapNotNull { annotation ->
                val mixin = annotation.parentOfType<PsiClass>() ?: return@mapNotNull null
                if (mixin == currentMixin || mixin.qualifiedName == currentMixin.qualifiedName) return@mapNotNull null
                val priority = annotation.priority
                if (priority >= currentPriority) return@mapNotNull null
                priority to mixin
            }
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun PsiMember.toTargetMember(mixin: PsiClass): MixinTargetMember? {
        val ownerName = mixin.internalName ?: return null
        val bytecode = mixin.bytecode
        val owner = bytecode ?: ClassNode().apply { name = ownerName }
        return when (this) {
            is PsiField -> {
                val descriptor = descriptor ?: return null
                val field = bytecode?.findFieldByName(name)?.takeIf { it.desc == descriptor }
                    ?: FieldNode(accessFlags, name, descriptor, null, null)
                FieldTargetMember(owner, field)
            }
            is PsiMethod -> {
                val descriptor = descriptor ?: return null
                val method = bytecode?.methods?.firstOrNull { it.name == name && it.desc == descriptor }
                    ?: MethodNode(accessFlags, name, descriptor, null, null)
                MethodTargetMember(owner, method)
            }
            else -> null
        }
    }

    private val PsiMember.isShadow: Boolean
        get() = modifierList?.hasAnnotation(MixinConstants.Annotations.SHADOW) == true

    private val PsiMember.memberDescriptor: String?
        get() = when (this) {
            is PsiField -> descriptor
            is PsiMethod -> descriptor
            else -> null
        }

    private val PsiMember.accessFlags: Int
        get() {
            var access = 0
            if (hasModifierProperty(PsiModifier.PUBLIC)) access = access or Opcodes.ACC_PUBLIC
            if (hasModifierProperty(PsiModifier.PROTECTED)) access = access or Opcodes.ACC_PROTECTED
            if (hasModifierProperty(PsiModifier.PRIVATE)) access = access or Opcodes.ACC_PRIVATE
            if (hasModifierProperty(PsiModifier.STATIC)) access = access or Opcodes.ACC_STATIC
            if (hasModifierProperty(PsiModifier.FINAL)) access = access or Opcodes.ACC_FINAL
            if (this is PsiField) {
                if (hasModifierProperty(PsiModifier.VOLATILE)) access = access or Opcodes.ACC_VOLATILE
                if (hasModifierProperty(PsiModifier.TRANSIENT)) access = access or Opcodes.ACC_TRANSIENT
            }
            if (this is PsiMethod) {
                if (hasModifierProperty(PsiModifier.ABSTRACT)) access = access or Opcodes.ACC_ABSTRACT
                if (hasModifierProperty(PsiModifier.NATIVE)) access = access or Opcodes.ACC_NATIVE
            }
            return access
        }

    private val PsiAnnotation.priority: Int
        get() = findDeclaredAttributeValue("priority")?.constantValue as? Int ?: DEFAULT_PRIORITY

    private companion object {
        const val DEFAULT_PRIORITY = 1000
    }
}
