package com.bawnorton.msp.reference

import com.bawnorton.msp.util.MixinSquaredConstants
import com.bawnorton.msp.util.NamedMixinBytecode
import com.bawnorton.msp.util.hasAnnotationByPrefix
import com.bawnorton.msp.util.simulateMergeInto
import com.demonwav.mcdev.platform.mixin.reference.DynamicSelectorParser
import com.demonwav.mcdev.platform.mixin.reference.MixinSelector
import com.demonwav.mcdev.platform.mixin.reference.parseMixinSelector
import com.demonwav.mcdev.platform.mixin.util.MemberInfo
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.findSourceElement
import com.demonwav.mcdev.platform.mixin.util.mixinTargets
import com.demonwav.mcdev.platform.mcp.fabricloom.FabricLoomData
import com.demonwav.mcdev.util.Quantifier
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.descriptor
import com.demonwav.mcdev.util.findContainingClass
import com.demonwav.mcdev.util.findContainingModifierList
import com.demonwav.mcdev.util.findModule
import com.demonwav.mcdev.util.findQualifiedClass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiModifierList
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path

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
    val mappingsPath: Path?,
    override val quantifier: Quantifier = Quantifier.Exact(0, 1),
) : MixinSelector {
    override fun getCustomOwner(owner: ClassNode): ClassNode {
        val mixin = findQualifiedClass(project, mixinName, GlobalSearchScope.allScope(project))?.bytecode
            ?: return owner

        val merged = mixin.simulateMergeInto(owner) {
            findQualifiedClass(
                project,
                it,
                GlobalSearchScope.allScope(project)
            )?.bytecode
        }
        return mappingsPath?.let { NamedMixinBytecode.remap(merged, it) } ?: merged
    }

    override fun findSourceMethod(
        owner: ClassNode,
        method: MethodNode,
        project: Project,
        scope: GlobalSearchScope,
        canDecompile: Boolean,
    ): PsiElement? {
        val mixin = findQualifiedClass(project, mixinName, scope)?.bytecode ?: return null
        val original = mixin.methods.firstOrNull {
            it.name == method.name && it.desc.replace("L${mixin.name};", "L${owner.name};") == method.desc
        } ?: return null
        return original.findSourceElement(mixin, project, scope, canDecompile)
    }

    override fun matchField(owner: String, name: String, desc: String): Boolean {
        return false
    }

    override fun matchMethod(owner: String, name: String, desc: String): Boolean {
        if (!this.mixinTargets.contains(owner)) return false
        val mixinOwner = mixinName.replace('.', '/')
        if (this.desc != null && desc != this.desc.replace("L$mixinOwner;", "L$owner;")) return false
        if (this.name != name) return false

        if (this.prefix != null) {
            val matchingMethods = findQualifiedClass(project, mixinName, GlobalSearchScope.allScope(project))
                ?.findMethodsByName(name, false) ?: return false
            for (method in matchingMethods) {
                if (method.descriptor?.replace("L$mixinOwner;", "L$owner;") != desc) continue
                if (!method.hasAnnotationByPrefix(prefix)) continue

                return true
            }
            return false
        }

        return true
    }

    override fun withQuantifier(quantifier: Quantifier) = copy(quantifier = quantifier)

    override val methodDescriptor = desc
    override val fieldDescriptor = null
    override val owner = mixinName.replace('.', '/')
}

fun targetHandlerSelectorFromAnnotation(targetHandlerAnnotation: PsiAnnotation, mixinTargets: List<String>): TargetHandlerSelector? {
    val targetMixin = targetHandlerAnnotation.findAttributeValue("mixin")
        ?.constantStringValue ?: return null

    val targetMethod = targetHandlerAnnotation.findAttributeValue("name") ?: return null
    val selector = parseMixinSelector(targetMethod)
    if (selector !is MemberInfo) return null // Don't try to match other selector references

    var prefix = targetHandlerAnnotation.findAttributeValue("prefix")?.constantStringValue
    if (prefix.isNullOrEmpty()) prefix = null
    val gradleData = targetHandlerAnnotation.findModule()?.let { GradleUtil.findGradleModuleData(it) }
    val mappingsPath = (gradleData?.children?.firstOrNull { it.key == FabricLoomData.KEY }?.data as? FabricLoomData)
        ?.tinyMappings?.toPath()
    return TargetHandlerSelector(
        targetHandlerAnnotation.project,
        targetMixin,
        mixinTargets,
        selector.name ?: return null,
        selector.descriptor,
        prefix,
        mappingsPath,
    )
}
