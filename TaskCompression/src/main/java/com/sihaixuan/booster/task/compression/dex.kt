package com.sihaixuan.booster.task.compression

import org.jf.baksmali.Adaptors.ClassDefinition
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.iface.ClassDef
import org.jf.util.IndentingWriter
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/22 22:35
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */

fun ClassDef.disassembleClass(options: BaksmaliOptions):List<String>?{
    /**
     * The path for the disassembly file is based on the package name
     * The class descriptor will look something like:
     * Ljava/lang/Object;
     * Where the there is leading 'L' and a trailing ';', and the parts of the
     * package name are separated by '/'
     */
    val classDescriptor = type

    //validate that the descriptor is formatted like we expect
    if(classDescriptor[0] != 'L' || classDescriptor[classDescriptor.length - 1] !=';'){
        return null
    }

    //create and initialize the top level string template
    val classDefinition = ClassDefinition(options, this)

    //write the disassembly


    try{
         ByteArrayOutputStream().use { baos->
             BufferedWriter(OutputStreamWriter(baos, "UTF8")).use {
                 val writer = IndentingWriter(it)
                 classDefinition.writeTo(writer)
                 writer.flush()
                 return baos.toString().split("\n")
             }
         }
    }catch (e : Exception){
        e.printStackTrace()
    }


    return null
}
