package com.sihaixuan.booster.task.compression

import com.didiglobal.booster.aapt2.BinaryParser
import java.io.File

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/3 15:15
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */
object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        val path = File("F:\\developer_workspace\\android__workspace\\NewStudy\\BoosterDemo\\app\\build\\intermediates\\res\\merged\\debug")
        path.listFiles().iterator().forEach {
            if(it.isFile){
                BinaryParser(it).apply {
                    println("${it.name} ---> magic = ${readInt()},version = ${readInt()} ,count = ${readInt()}")
                }
            }

        }
    }

}
