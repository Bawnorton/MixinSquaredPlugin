package com.bawnorton.msp.handlers.injectionPoint


import com.bawnorton.msp.util.getAnnotationByPrefix
import com.bawnorton.msp.util.hasAnnotationByPrefix
import com.demonwav.mcdev.platform.mixin.reference.parseMixinSelector
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.findSourceElement
import com.demonwav.mcdev.util.constantStringValue
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

class TargetHandlerResolver(
    private val targetHandler: PsiAnnotation
) {
    fun resolveMixinTarget(): ClassNode? {
        val project = targetHandler.project
        val className = targetHandler.findAttributeValue("mixin")?.constantStringValue ?: return null
        val targetClass =
            JavaPsiFacade.getInstance(project).findClass(className, targetHandler.resolveScope) ?: return null
        return targetClass.bytecode
    }

    fun resolveNameTargets(classNode: ClassNode): List<MethodNode>? {
        val targetMethodInfo = targetHandler.findAttributeValue("name") ?: return null
        val selector = parseMixinSelector(targetMethodInfo) ?: return null
        return classNode.methods.filter { selector.matchMethod(it, classNode) }
    }

    fun resolvePrefixTargets(prefix: String): List<PsiAnnotation>? {
        val target = resolveMixinTarget() ?: return null
        val targetMethods = target.methods
            .mapNotNull {
                it.findSourceElement(
                    target,
                    targetHandler.project,
                    targetHandler.resolveScope,
                    canDecompile = false
                )
            }
            .filterIsInstance<PsiMethod>()
            .filter { it.hasAnnotationByPrefix(prefix) }
        return targetMethods.mapNotNull { it.getAnnotationByPrefix(prefix) }
    }
}