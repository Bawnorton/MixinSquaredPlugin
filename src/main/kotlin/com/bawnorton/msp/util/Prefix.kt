package com.bawnorton.msp.util

import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod

private val prefixMap: Map<String, String> = mapOf(
    "handler" to MixinConstants.Annotations.INJECT,
    "modify" to MixinConstants.Annotations.MODIFY_ARG,
    "args" to MixinConstants.Annotations.MODIFY_ARGS,
    "constant" to MixinConstants.Annotations.MODIFY_CONSTANT,
    "localvar" to MixinConstants.Annotations.MODIFY_VARIABLE,
    "redirect" to MixinConstants.Annotations.REDIRECT,
    "modifyExpressionValue" to MixinConstants.MixinExtras.MODIFY_EXPRESSION_VALUE,
    "modifyReciever" to MixinConstants.MixinExtras.MODIFY_RECEIVER,
    "modifyReturnValue" to MixinConstants.MixinExtras.MODIFY_RETURN_VALUE,
    "wrapWithCondition" to MixinConstants.MixinExtras.WRAP_WITH_CONDITION,
    "wrapOperation" to MixinConstants.MixinExtras.WRAP_OPERATION,
)

fun PsiMethod.getAnnotationByPrefix(prefix: String): PsiAnnotation? {
    val annotationName = prefixMap[prefix] ?: return null
    return this.getAnnotation(annotationName)
}

fun PsiMethod.hasAnnotationByPrefix(prefix: String): Boolean {
    return this.getAnnotationByPrefix(prefix) != null
}

fun isPrefix(prefix: String): Boolean {
    return prefixMap.containsKey(prefix)
}

fun getSimpleAnnotationName(prefix: String): String? {
    val annotationName = prefixMap[prefix] ?: return null
    return annotationName.substringAfterLast('.')
}

fun getPossiblePrefixes(): Set<String> {
    return prefixMap.keys
}