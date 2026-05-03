package com.bawnorton.msp.reference

import com.bawnorton.msp.util.MixinSquaredConstants
import com.bawnorton.msp.util.hasAnnotationByPrefix
import com.bawnorton.msp.util.simulateMergeInto
import com.demonwav.mcdev.platform.mixin.reference.DynamicSelectorParser
import com.demonwav.mcdev.platform.mixin.reference.MixinSelector
import com.demonwav.mcdev.platform.mixin.reference.parseMixinSelector
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.mixinTargets
import com.demonwav.mcdev.util.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.objectweb.asm.tree.ClassNode

// @MixinSquared:Handler

class TargetHandlerSelectorParser : DynamicSelectorParser("MixinSquared:Handler") {
    override fun parseDynamic(args: String, context: PsiElement): MixinSelector? {
        val targetHandler = findTargetHandlerAnnotation(context) ?: return null
        val mixinTargets = context.findContainingClass()?.mixinTargets?.map { it.name } ?: return null
        return targetHandlerSelectorFromAnnotation(targetHandler, mixinTargets)
    }

    private fun findTargetHandlerAnnotation(context: PsiElement): PsiAnnotation? {
        val modifierList = PsiTreeUtil.getParentOfType(context, PsiModifierList::class.java) ?: return null
        return modifierList.findAnnotation(MixinSquaredConstants.TARGET_HANDLER)
    }

    override fun onCompleted(editor: Editor, reference: PsiLiteral) {
        val modifierList = reference.findContainingModifierList() ?: return
        if (modifierList.hasAnnotation(MixinSquaredConstants.TARGET_HANDLER)) {
            return
        }

        val project = reference.project

        val targetHandlerAnnotation = modifierList.addAfter(
            JavaPsiFacade.getElementFactory(project)
                .createAnnotationFromText("@${MixinSquaredConstants.TARGET_HANDLER}(mixin=\"\")", reference),
            null
        )

        // add imports and reformat
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(targetHandlerAnnotation)
        JavaCodeStyleManager.getInstance(project).optimizeImports(modifierList.containingFile)
        val formattedModifierList = CodeStyleManager.getInstance(project).reformat(modifierList) as PsiModifierList

        // move the caret to @TargetHandler(mixin="<caret>")
        val formattedTargetHandlerAnnotation = formattedModifierList.findAnnotation(MixinSquaredConstants.TARGET_HANDLER)
            ?: return
        val targetHandlerLiteral = formattedTargetHandlerAnnotation.findDeclaredAttributeValue("mixin") ?: return
        editor.caretModel.moveToOffset(targetHandlerLiteral.textRange.startOffset + 1)
    }
}

data class TargetHandlerSelector(
    val project: Project,
    val mixinName: String,
    val mixinTargets: List<String>,
    val name: String,
    val desc: String?,
    val prefix: String?,
) : MixinSelector {
    /**
     * We want to resolve methods inside the target mixin, not the class that the @Mixin annotation specifies.
     */
    override fun getCustomOwner(owner: ClassNode): ClassNode {
        val mixin = findQualifiedClass(project, mixinName, GlobalSearchScope.allScope(project))?.bytecode
            ?: return owner

        val result = mixin.simulateMergeInto(owner) {
            findQualifiedClass(
                project,
                it,
                GlobalSearchScope.allScope(project)
            )?.bytecode
        }
        return result
    }

    override fun matchField(owner: String, name: String, desc: String): Boolean {
        return false
    }

    override fun matchMethod(owner: String, name: String, desc: String): Boolean {
        if (!this.mixinTargets.contains(owner)) return false
        // if (this.owner != owner) return false
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

fun targetHandlerSelectorFromAnnotation(targetHandlerAnnotation: PsiAnnotation, mixinTargets: List<String>): TargetHandlerSelector? {
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
        mixinTargets,
        selector.name,
        selector.descriptor,
        prefix
    )
}