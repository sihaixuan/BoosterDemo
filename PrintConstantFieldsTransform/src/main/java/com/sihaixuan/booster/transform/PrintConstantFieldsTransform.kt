package com.sihaixuan.booster.transform

import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.kotlinx.touch
import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.ClassTransformer
import com.google.auto.service.AutoService
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.io.File
import java.io.PrintWriter

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/1 14:58
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */

//@AutoService(ClassTransformer::class)
class PrintConstantFieldsTransform :ClassTransformer{
    private lateinit var logger: PrintWriter

    override fun onPreTransform(context: TransformContext) {
        println("--------------------------------TestTransform-----------------------------------")
        this.logger =context.reportsDir.file("${this::class.java.simpleName}").file("report.txt").touch().printWriter()

    }

    override fun onPostTransform(context: TransformContext) {}


    override fun transform(context: TransformContext, klass: ClassNode): ClassNode {
        klass.printConstantFields()
        return klass
    }

    private fun ClassNode.printConstantFields() {
        fields.map {
            it as FieldNode
        }.filter {
            0 != (Opcodes.ACC_STATIC and it.access) && 0 != (Opcodes.ACC_FINAL and it.access) //&& it.value != null
        }.forEach {
            //        fields.remove(it)
            logger.println("field: `$name.${it.name} : ${it.desc}` = ${it.valueAsString()}")
        }

        logger.close()
    }
    private fun FieldNode.valueAsString() = when {
        this.value is String -> "\"${this.value}\""
        this.value == null -> "null"
        else -> this.value.toString()
    }

}

