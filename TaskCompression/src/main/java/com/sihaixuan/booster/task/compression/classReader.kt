package com.sihaixuan.booster.task.compression

import com.didiglobal.booster.kotlinx.redirect

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/26 9:48
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */

import com.didiglobal.booster.util.search
import org.gradle.api.logging.Logging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile

private val logger = Logging.getLogger("transform")

/**
 * read this file or directory to  ClassNode
 *
 * @param transformer The byte data transformer
 */
fun File.readClass(transformer: (ClassNode) -> Unit = {   }) {
    when {
        isDirectory -> {
            this.search().forEach {
                it.readClass( transformer)
            }
        }
        isFile -> {
            when (this.extension.toLowerCase()) {
                "jar" -> {
                    JarFile(this).use { jar ->
                        jar.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory) {
                                when (entry.name.substringAfterLast('.', "")) {
                                    "class" -> jar.getInputStream(entry).use { src ->
                                        logger.info("readClass ${this.absolutePath}!/${entry.name}")
                                        src.transform(transformer)
                                    }

                                }
                            }
                        }
                    }
                }
                "class" -> inputStream().use {
                    logger.info("Transforming ${this.absolutePath}")
                    it.transform(transformer)
                }

            }
        }
        else -> TODO("Unexpected file: ${this.absolutePath}")
    }
}

private fun InputStream.transform(transformer: (ClassNode) -> Unit) {
    ClassNode().also { klass ->
        ClassReader(readBytes()).accept(klass, 0)
    }.apply {
        transformer(this)
    }



}

private const val DEFAULT_BUFFER_SIZE = 8 * 1024

private fun InputStream.readBytes(estimatedSize: Int = DEFAULT_BUFFER_SIZE): ByteArray {
    val buffer = ByteArrayOutputStream(Math.max(estimatedSize, this.available()))
    copyTo(buffer)
    return buffer.toByteArray()
}

