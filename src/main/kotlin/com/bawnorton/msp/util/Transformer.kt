package com.bawnorton.msp.util

import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode

private fun String.toDesc() = "L${replace('.', '/')};"

private val MIXIN_DESC = MixinConstants.Annotations.MIXIN.toDesc()
private val SHADOW_DESC = MixinConstants.Annotations.SHADOW.toDesc()
private val INTRINSIC_DESC = MixinConstants.Annotations.INTRINSIC.toDesc()
private val MUTABLE_DESC = MixinConstants.Annotations.MUTABLE.toDesc()

private val INJECTOR_DESCS: Set<String> = setOf(
    MixinConstants.Annotations.INJECT,
    MixinConstants.Annotations.MODIFY_ARG,
    MixinConstants.Annotations.MODIFY_ARGS,
    MixinConstants.Annotations.MODIFY_CONSTANT,
    MixinConstants.Annotations.MODIFY_VARIABLE,
    MixinConstants.Annotations.REDIRECT,
    MixinConstants.Annotations.ACCESSOR,
    MixinConstants.Annotations.INVOKER,
    MixinConstants.MixinExtras.WRAP_OPERATION,
    MixinConstants.MixinExtras.MODIFY_EXPRESSION_VALUE,
    MixinConstants.MixinExtras.MODIFY_RECEIVER,
    MixinConstants.MixinExtras.MODIFY_RETURN_VALUE,
    MixinConstants.MixinExtras.WRAP_WITH_CONDITION,
).map { it.toDesc() }.toSet()

private fun List<AnnotationNode>.hasDesc(desc: String) = any { it.desc == desc }

private fun AnnotationNode.getStringAttr(key: String): String? {
    val v = values ?: return null
    val i = v.indexOf(key)
    return if (i >= 0 && i + 1 < v.size) v[i + 1] as? String else null
}

private fun MethodNode.hasAnnotation(desc: String) =
    visibleAnnotations?.hasDesc(desc) == true || invisibleAnnotations?.hasDesc(desc) == true

private fun MethodNode.isShadow() = hasAnnotation(SHADOW_DESC)
private fun MethodNode.isIntrinsic() = hasAnnotation(INTRINSIC_DESC)
private fun MethodNode.isInjector() = INJECTOR_DESCS.any { hasAnnotation(it) }

private fun FieldNode.hasAnnotation(desc: String) =
    visibleAnnotations?.hasDesc(desc) == true || invisibleAnnotations?.hasDesc(desc) == true

private fun FieldNode.isShadow() = hasAnnotation(SHADOW_DESC)
private fun FieldNode.isMutable() = hasAnnotation(MUTABLE_DESC)

private fun FieldNode.shadowTargetName(): String {
    val annotation = (visibleAnnotations ?: emptyList()).firstOrNull { it.desc == SHADOW_DESC }
        ?: (invisibleAnnotations ?: emptyList()).firstOrNull { it.desc == SHADOW_DESC }
        ?: return name
    val prefix = annotation.getStringAttr("prefix") ?: return name
    return if (prefix.isNotEmpty() && name.startsWith(prefix)) name.substring(prefix.length) else name
}

private fun ClassNode.hasAnnotation(desc: String) =
    visibleAnnotations?.hasDesc(desc) == true || invisibleAnnotations?.hasDesc(desc) == true

private fun ClassNode.isMixin() = hasAnnotation(MIXIN_DESC)

private fun ClassNode.getMixinTargetNames(): Set<String> {
    val annotation = (visibleAnnotations ?: emptyList()).firstOrNull { it.desc == MIXIN_DESC }
        ?: (invisibleAnnotations ?: emptyList()).firstOrNull { it.desc == MIXIN_DESC }
        ?: return emptySet()
    val v = annotation.values ?: return emptySet()
    val result = mutableSetOf<String>()
    for (i in 0 until v.size - 1 step 2) {
        val key = v[i] as? String ?: continue
        when (key) {
            "value" -> (v[i + 1] as? List<*>)?.forEach { if (it is Type) result.add(it.internalName) }
            "targets" -> (v[i + 1] as? List<*>)?.forEach { if (it is String) result.add(it.replace('.', '/')) }
        }
    }
    return result
}

fun ClassNode.simulateMergeInto(
    targetClass: ClassNode,
    classResolver: ((String) -> ClassNode?)? = null,
): ClassNode = mergeInto(targetClass, classResolver, mutableSetOf())

private fun ClassNode.mergeInto(
    targetClass: ClassNode,
    classResolver: ((String) -> ClassNode?)?,
    visited: MutableSet<String>,
): ClassNode {
    if (!visited.add(name)) return targetClass

    val base = superMixinBase(targetClass, classResolver, visited)

    val remapped = remapReferences(name, targetClass.name)

    val result = ClassNode()
    result.name = base.name
    result.superName = base.superName
    result.access = base.access
    result.signature = base.signature
    result.version = base.version

    result.interfaces = ((base.interfaces ?: emptyList()) + (remapped.interfaces ?: emptyList()))
        .distinct().toMutableList()

    val fields = (base.fields ?: emptyList()).toMutableList()
    for (field in (remapped.fields ?: emptyList())) applyField(field, fields)
    result.fields = fields

    val methods = (base.methods ?: emptyList()).toMutableList()
    for (method in (remapped.methods ?: emptyList())) applyMethod(method, methods)
    result.methods = methods

    return result
}

private fun ClassNode.remapReferences(fromName: String, toName: String): ClassNode {
    if (fromName == toName) return this
    val result = ClassNode()
    result.name = name
    result.superName = superName
    result.access = access
    result.signature = signature?.replace("L$fromName;", "L$toName;")
    result.version = version
    result.interfaces = interfaces?.toMutableList() ?: mutableListOf()
    result.visibleAnnotations = visibleAnnotations
    result.invisibleAnnotations = invisibleAnnotations
    result.fields = fields?.map { it.remapReferences(fromName, toName) }?.toMutableList() ?: mutableListOf()
    result.methods = methods?.map { it.remapReferences(fromName, toName) }?.toMutableList() ?: mutableListOf()
    return result
}

private fun FieldNode.remapReferences(fromName: String, toName: String): FieldNode {
    val result = FieldNode(
        access, name,
        desc.replace("L$fromName;", "L$toName;"),
        signature?.replace("L$fromName;", "L$toName;"),
        value,
    )
    result.visibleAnnotations = visibleAnnotations
    result.invisibleAnnotations = invisibleAnnotations
    return result
}

private fun MethodNode.remapReferences(fromName: String, toName: String): MethodNode {
    val labelMap: Map<LabelNode, LabelNode> = instructions
        .filterIsInstance<LabelNode>()
        .associateWith { LabelNode() }

    val result = MethodNode(
        access, name,
        desc.replace("L$fromName;", "L$toName;"),
        signature?.replace("L$fromName;", "L$toName;"),
        exceptions?.map { if (it == fromName) toName else it }?.toTypedArray(),
    )

    for (insn in instructions) {
        result.instructions.add(insn.remapReferences(fromName, toName, labelMap))
    }

    result.tryCatchBlocks = tryCatchBlocks?.map { b ->
        TryCatchBlockNode(
            labelMap[b.start] ?: b.start,
            labelMap[b.end] ?: b.end,
            b.handler?.let { labelMap[it] ?: it },
            b.type?.let { if (it == fromName) toName else it },
        )
    }?.toMutableList()

    result.localVariables = localVariables?.map { lv ->
        LocalVariableNode(
            lv.name,
            lv.desc.replace("L$fromName;", "L$toName;"),
            lv.signature?.replace("L$fromName;", "L$toName;"),
            labelMap[lv.start] ?: lv.start,
            labelMap[lv.end] ?: lv.end,
            lv.index,
        )
    }?.toMutableList()

    result.visibleAnnotations = visibleAnnotations
    result.invisibleAnnotations = invisibleAnnotations
    result.visibleParameterAnnotations = visibleParameterAnnotations
    result.invisibleParameterAnnotations = invisibleParameterAnnotations
    result.visibleLocalVariableAnnotations = visibleLocalVariableAnnotations
    result.invisibleLocalVariableAnnotations = invisibleLocalVariableAnnotations
    result.maxStack = maxStack
    result.maxLocals = maxLocals
    return result
}

private fun AbstractInsnNode.remapReferences(
    fromName: String,
    toName: String,
    labelMap: Map<LabelNode, LabelNode>,
): AbstractInsnNode {
    val cloned = clone(labelMap)
    when (cloned) {
        is MethodInsnNode -> {
            if (cloned.owner == fromName) cloned.owner = toName
            cloned.desc = cloned.desc.replace("L$fromName;", "L$toName;")
        }
        is FieldInsnNode -> {
            if (cloned.owner == fromName) cloned.owner = toName
            cloned.desc = cloned.desc.replace("L$fromName;", "L$toName;")
        }
        is TypeInsnNode -> if (cloned.desc == fromName) cloned.desc = toName
        is MultiANewArrayInsnNode -> cloned.desc = cloned.desc.replace("L$fromName;", "L$toName;")
        is LdcInsnNode -> {
            val c = cloned.cst
            if (c is Type && c.sort == Type.OBJECT && c.internalName == fromName)
                cloned.cst = Type.getObjectType(toName)
        }
    }
    return cloned
}

private fun ClassNode.superMixinBase(
    targetClass: ClassNode,
    classResolver: ((String) -> ClassNode?)?,
    visited: MutableSet<String>,
): ClassNode {
    classResolver ?: return targetClass
    val superName = superName?.takeIf { it != "java/lang/Object" } ?: return targetClass
    val superNode = classResolver(superName)?.takeIf { it.isMixin() } ?: return targetClass
    val superTarget = superNode.findTargetInHierarchy(targetClass, classResolver) ?: targetClass
    return superNode.mergeInto(superTarget, classResolver, visited)
}

private fun ClassNode.findTargetInHierarchy(
    targetClass: ClassNode,
    classResolver: (String) -> ClassNode?,
): ClassNode? {
    val targetNames = getMixinTargetNames()
    var current: ClassNode? = targetClass
    while (current != null) {
        if (current.name in targetNames) return current
        val next = current.superName?.takeIf { it != "java/lang/Object" } ?: break
        current = classResolver(next)
    }
    return null
}

private fun applyField(field: FieldNode, target: MutableList<FieldNode>) {
    if (field.isShadow()) {
        if (field.isMutable()) {
            val targetName = field.shadowTargetName()
            target.find { it.name == targetName }
                ?.let { it.access = it.access and Opcodes.ACC_FINAL.inv() }
        }
        return
    }
    target.removeIf { it.name == field.name }
    target.add(field)
}

private fun applyMethod(method: MethodNode, target: MutableList<MethodNode>) {
    if (method.name == "<init>" || method.name == "<clinit>") return
    if (method.isShadow()) return

    if (method.isIntrinsic()) {
        if (target.none { it.name == method.name && it.desc == method.desc }) target.add(method)
        return
    }

    if (method.isInjector()) {
        target.add(method)
        return
    }

    target.removeIf { it.name == method.name && it.desc == method.desc }
    target.add(method)
}