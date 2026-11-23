package com.bawnorton.msp.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object NamedMixinBytecode {
    private data class MappingFile(val modified: FileTime, val tree: MemoryMappingTree, val named: Int, val intermediary: Int)

    private val mappings = ConcurrentHashMap<Path, MappingFile>()

    fun remap(owner: ClassNode, path: Path): ClassNode {
        val modified = runCatching { Files.getLastModifiedTime(path) }.getOrNull() ?: return owner
        val mapping = mappings.compute(path) { _, cached ->
            if (cached?.modified == modified) {
                cached
            } else {
                runCatching {
                    val tree = MemoryMappingTree()
                    MappingReader.read(path, tree)
                    val named = tree.getNamespaceId("named")
                    val intermediary = tree.getNamespaceId("intermediary")
                    if (named == -2 || intermediary == -2) null else MappingFile(modified, tree, named, intermediary)
                }.getOrNull()
            }
        } ?: return owner

        val result = ClassNode()
        owner.accept(result)
        for (method in result.methods) {
            for (insn in method.instructions) {
                when (insn) {
                    is FieldInsnNode -> {
                        val clazz = mapping.tree.getClass(insn.owner, mapping.named) ?: continue
                        insn.name = clazz.getField(insn.name, null, mapping.intermediary)
                            ?.getName(mapping.named) ?: insn.name
                    }
                    is MethodInsnNode -> {
                        val clazz = mapping.tree.getClass(insn.owner, mapping.named) ?: continue
                        val desc = mapping.tree.mapDesc(insn.desc, mapping.named, mapping.intermediary)
                        insn.name = clazz.getMethod(insn.name, desc, mapping.intermediary)
                            ?.getName(mapping.named) ?: insn.name
                    }
                }
            }
        }
        return result
    }
}
