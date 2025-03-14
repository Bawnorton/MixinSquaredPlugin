package com.bawnorton.msp.handlers


import com.bawnorton.msp.util.getAnnotationByPrefix
import com.bawnorton.msp.util.hasAnnotationByPrefix
import com.demonwav.mcdev.platform.mixin.reference.parseMixinSelector
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.findOrConstructSourceMethod
import com.demonwav.mcdev.util.constantStringValue
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import org.objectweb.asm.tree.ClassNode

class TargetHandlerResolver(
    private val targetHandler: PsiAnnotation
) {
    fun resolveMixinTarget(): ClassNode? {
        val project = targetHandler.project
        val className = targetHandler.findAttributeValue("mixin")?.constantStringValue ?: return null
        val targetClass = JavaPsiFacade.getInstance(project).findClass(className, targetHandler.resolveScope) ?: return null
        return targetClass.bytecode
    }

    fun resolveNameTargets(): List<PsiElement>? {
        val target = resolveMixinTarget() ?: return null
        val targetMethodInfo = targetHandler.findAttributeValue("name") ?: return null
        val selector = parseMixinSelector(targetMethodInfo) ?: return null
        val targetNodes = target.methods.filter { selector.matchMethod(it, target) }
        val sources = targetNodes.mapNotNull {
            it.findOrConstructSourceMethod(
                target,
                targetHandler.project,
                targetHandler.resolveScope,
                canDecompile = true
            )
        }
        return sources;
    }

    fun resolvePrefixTargets(prefix: String): List<PsiAnnotation>? {
        val target = resolveMixinTarget() ?: return null
        val targetMethods = target.methods
            .mapNotNull {
                it.findOrConstructSourceMethod(
                    target,
                    targetHandler.project,
                    targetHandler.resolveScope,
                    canDecompile = true
                )
            }
            .filter { it.hasAnnotationByPrefix(prefix) }
        return targetMethods.mapNotNull { it.getAnnotationByPrefix(prefix) }
    }
}