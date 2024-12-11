package com.bawnorton.msp.reference

import com.bawnorton.msp.util.hasAnnotationByPrefix
import com.bawnorton.msp.util.MixinSquaredConstants
import com.demonwav.mcdev.platform.mixin.reference.DynamicSelectorParser
import com.demonwav.mcdev.platform.mixin.reference.MixinSelector
import com.demonwav.mcdev.platform.mixin.reference.parseMixinSelector
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.util.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.objectweb.asm.tree.ClassNode

// @MixinSquared:Handler

class TargetHandlerSelectorParser : DynamicSelectorParser("MixinSquared:Handler") {
    override fun parseDynamic(args: String, context: PsiElement): MixinSelector? {
        val targetHandler = findTargetHandlerAnnotation(context) ?: return null
        return targetHandlerSelectorFromAnnotation(targetHandler)
    }

    private fun findTargetHandlerAnnotation(context: PsiElement): PsiAnnotation? {
        val method = context.parentOfType<PsiMethod>() ?: return null
        return method.findAnnotation(MixinSquaredConstants.TARGET_HANDLER)
    }

    companion object {
        fun targetHandlerSelectorFromAnnotation(targetHandlerAnnotation: PsiAnnotation): TargetHandlerSelector? {
            val targetMixin = targetHandlerAnnotation.findAttributeValue("mixin")
                ?.constantStringValue ?: return null

            val targetMethod = targetHandlerAnnotation.findAttributeValue("name") ?: return null
            val selector = parseMixinSelector(targetMethod)
            if (selector !is MemberReference) return null // Don't try to match other selector references

            var prefix = targetHandlerAnnotation.findAttributeValue("prefix")?.constantStringValue
            if (prefix.isNullOrEmpty()) prefix = null
            return TargetHandlerSelector(
                targetHandlerAnnotation.project,
                targetMixin,
                selector.name,
                selector.descriptor,
                prefix
            )
        }
    }
}

data class TargetHandlerSelector(
    val project: Project,
    val mixinName: String,
    val name: String,
    val desc: String?,
    val prefix: String?,
) : MixinSelector {
    /**
     * We want to resolve methods inside the target mixin, not the class that the @Mixin annotation specifies.
     */
    override fun getCustomOwner(owner: ClassNode): ClassNode {
        val mixin = findQualifiedClass(project, mixinName, GlobalSearchScope.allScope(project))?.bytecode
        return mixin ?: owner
    }

    override fun matchField(owner: String, name: String, desc: String): Boolean {
        return false
    }

    override fun matchMethod(owner: String, name: String, desc: String): Boolean {
        if (this.owner != owner) return false
        if (this.desc != null && desc != this.desc) return false
        if (this.name != name) return false

        if (this.prefix != null) {
            val matchingMethods = findQualifiedClass(project, mixinName, GlobalSearchScope.allScope(project))
                ?.findMethodsByName(name, false) ?: return false
            for (method in matchingMethods) {
                if (method.descriptor != desc) continue
                if (!method.hasAnnotationByPrefix(prefix)) continue

                return true
            }
            return false
        }

        return true
    }

    override val displayName = name
    override val methodDescriptor = desc
    override val fieldDescriptor = null
    override val owner = mixinName.replace('.', '/')
}