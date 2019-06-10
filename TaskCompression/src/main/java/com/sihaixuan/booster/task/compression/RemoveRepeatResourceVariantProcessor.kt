package com.sihaixuan.booster.task.compression

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.gradle.processedRes
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.gradle.scope
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.kotlinx.touch


import com.didiglobal.booster.task.spi.VariantProcessor
import com.didiglobal.booster.util.search
import com.google.auto.service.AutoService
import org.gradle.api.initialization.Settings
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import pink.madis.apk.arsc.StringPoolChunk
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.logging.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/7 23:18
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */
@AutoService(VariantProcessor::class)
class RemoveRepeatResourceVariantProcessor: VariantProcessor  {
    override fun process(variant: BaseVariant) {

//         variant.packageAndroidTask.resourceFiles.files.forEach {
//            println("RemoveRepeatResourceVariantProcessor : ${it.absolutePath}/${it.name}")
//        }

        variant.project.rootProject.gradle.settingsEvaluated{
            println("RemoveRepeatResourceVariantProcessor settingsEvaluated")

        }

        variant.packageAndroidTask.doFirst{
            val results = RemoveRepeatResourceResults()
            variant.removeRepeatResources(it.logger,results)
            variant.generateReport(results)
        }

    }
}


/**
 *
 * Generates report with format like the following:
 *
 * deleted zipRetry name | size | compressed size
 */
private fun BaseVariant.generateReport(results: RemoveRepeatResourceResults) {
    var totalSize : Long= 0
    val maxWidth0 = results.map { it.name.length }.max() ?: 0
    val maxWidth1 = (results.map { it.size.toString().length }.max() ?: 0) + 6
    val maxWidth2 = (results.map { it.compressionSize.toString().length }.max() ?: 0) + 6
    project.buildDir.file("reports", "RemoveRepeatResource", name, "report.txt").touch().printWriter().use{ fileLogger ->
        results.forEach { entry ->
            println("deleted ${entry.name.padEnd(maxWidth0)} ${entry.size.toString().padStart(maxWidth1)} ${entry.compressionSize.toString().padStart(maxWidth2)}")
            fileLogger.println("deleted ${entry.name.padEnd(maxWidth0)} ${entry.size.toString().padStart(maxWidth1)} ${entry.compressionSize.toString().padStart(maxWidth2)}")

            totalSize += if(entry.size == entry.compressionSize) entry.size else entry.compressionSize

        }
        println("all deleted size : ${totalSize / 1024} kb")
        fileLogger.println("all deleted size : ${totalSize / 1024} kb")


    }




}


private fun BaseVariant.removeRepeatResources(logger:Logger,results:RemoveRepeatResourceResults){

    val files = scope.processedRes.search {
        it.name.startsWith(SdkConstants.FN_RES_BASE) && it.extension == SdkConstants.EXT_RES
    }
    files.parallelStream().forEach { ap_ ->
        doRemoveRepeatResources(ap_,logger,results)
    }


}

private fun doRemoveRepeatResources(apFile:File,logger:Logger,results:RemoveRepeatResourceResults){
    val entryName = "resources.arsc"
    val arscFile = File(apFile.parent,entryName)

    if(arscFile.exists()){
        arscFile.delete()
    }

    try{
        //解压resources.arsc条目
        val extractResult = apFile.extractZipEntry(entryName,arscFile)
        if(!extractResult.success){
            logger.error("${apFile.name} $entryName zipEntry extract failed!")
            return
        }

        //找出重复资源
        val duplicatedResources = apFile.findDuplicatedResources()

        if(duplicatedResources == null || duplicatedResources.isEmpty()){
            logger.error("${apFile.name} $entryName do not has duplicatedResources")
            return
        }


        //根据找到的重复资源集合，保留resources.ap_压缩重复的资源的第一个ZipEntry,其他重复ZipEntry删除
        //通过android-chunk-utils修改resources.arsc，把这些重复的资源都重定向到同一个文件上；
        FileInputStream(arscFile).use{
            ResourceFile.fromInputStream(it).apply {
                val chucks = this.chunks
                val toBeReplacedResourceMap = HashMap<String,String>(1024)
                val removedDuplicatedEntries = ArrayList<DuplicatedEntry>()

                duplicatedResources.forEach{ mapEntry ->
                    mapEntry.value.apply {
                        val retained = mapEntry.value[0]
                        val removedEntryNames = ArrayList<String>()
                        forEach { entry ->
                            if(retained != entry){
                                // 保留第一个资源 删除重复的资源
                                removedEntryNames.add(entry.name)
                                toBeReplacedResourceMap[entry.name] = retained.name
                                removedDuplicatedEntries.add(entry)
                            }
                        }

                        apFile.removeZipEntry(removedEntryNames)


                        chucks.filter{ chunk->
                            chunk is ResourceTableChunk
                        }.map{ chunk->
                            chunk as ResourceTableChunk
                        }.forEach { chunk ->
                            val stringPoolChunk = chunk.stringPool
                            for(index in 0 until stringPoolChunk.stringCount){
                                val key = stringPoolChunk.getString(index)
                                if(toBeReplacedResourceMap.containsKey(key)){
                                    //把这些重复的资源都重定向到同一个文件上
                                    stringPoolChunk.setString(index,retained.name)
                                }
                            }
                        }

                    }


                }

                results.addAll(removedDuplicatedEntries)

                arscFile.delete()

                //把ResourceFile 中数据，写入到arscFile去
                FileOutputStream(arscFile).use {
                    BufferedOutputStream(it).use { bufferedOutputStream ->
                        bufferedOutputStream.write(this.toByteArray())
                    }
                }

                //从resources.ap_压缩包中删除resources.arsc条目
                apFile.removeZipEntry(entryName)

                //把修改后的resources.arsc文件,打入压缩包
                apFile.addZipEntry(arscFile,entryName,extractResult.entryMethod)

                arscFile.delete()

            }

        }

    }catch (e:Exception){
        e.printStackTrace()
    }


}


private fun StringPoolChunk.setString(index:Int, value:String){
    try{
        val field = javaClass.getDeclaredField("strings")
        field.setAccessible(true)
        val list = field.get(this) as MutableList<String>
        list.set(index,value)
    }catch (e:Exception){
        e.printStackTrace()
    }

}

private fun File.extractZipEntry(entryName:String,desFile:File):ExtractZipEntryResult{
    val parentFile = desFile.parentFile
    if(!parentFile.exists()){
        parentFile.mkdirs()
    }
    val result = ExtractZipEntryResult(entryName)
    BufferedOutputStream(desFile.outputStream()).use { out ->
        ZipFile(this).use{ zip ->
            val targetEntry = zip.getEntry(entryName)
            if(targetEntry != null){
                result.entryMethod = targetEntry.method
                BufferedInputStream(zip.getInputStream(targetEntry)).use{
                    val bytes = ByteArray(1024)
                    var count = it.read(bytes)
                    while(count != -1){
                        out.write(bytes,0,count)
                        count = it.read(bytes)
                    }

                }
            }
        }
    }

    result.success = desFile.exists() && desFile.length() > 0

    return result
}

private fun File.removeZipEntry(entryName:String){
    val dest = File.createTempFile(SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP, SdkConstants.DOT_RES)
    ZipOutputStream(dest.outputStream()).use { output ->
        ZipFile(this).use { zip ->
            zip.entries().asSequence().forEach { origin ->
                if(origin.name !=(entryName)){
                    val target = ZipEntry(origin.name).apply {
                        size = origin.size
                        crc = origin.crc
                        comment = origin.comment
                        extra = origin.extra
                        method =  origin.method
                    }

                    output.putNextEntry(target)
                    zip.getInputStream(origin).use {
                        it.copyTo(output)
                    }

                }
                output.closeEntry()

            }
        }
    }

    if (this.delete()) {
        if (!dest.renameTo(this)) {
            dest.copyTo(this, true)
        }
    }
}

private fun File.removeZipEntry(entryNames:List<String>){
    val dest = File.createTempFile(SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP, SdkConstants.DOT_RES)
    ZipOutputStream(dest.outputStream()).use { output ->
        ZipFile(this).use { zip ->
            zip.entries().asSequence().forEach { origin ->
                if(!entryNames.contains(origin.name)){
                    val target = ZipEntry(origin.name).apply {
                        size = origin.size
                        crc = origin.crc
                        comment = origin.comment
                        extra = origin.extra
                        method =  origin.method
                    }

                    output.putNextEntry(target)
                    zip.getInputStream(origin).use {
                        it.copyTo(output)
                    }

                }
                output.closeEntry()

            }
        }
    }

    if (this.delete()) {
        if (!dest.renameTo(this)) {
            dest.copyTo(this, true)
        }
    }
}

private fun File.addZipEntry(file:File,entryName:String,entryMethod :Int = ZipEntry.DEFLATED){
    var dest = File.createTempFile(SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP, SdkConstants.DOT_RES)
    ZipOutputStream(dest.outputStream()).use { output ->
        ZipFile(this).use { zip ->
            zip.entries().asSequence().forEach { origin ->
                val target = ZipEntry(origin.name).apply {
                    size = origin.size
                    crc = origin.crc
                    comment = origin.comment
                    extra = origin.extra
                    method =  origin.method
                }

                output.putNextEntry(target)
                zip.getInputStream(origin).use {
                    it.copyTo(output)
                }
                output.closeEntry()

            }

            val addEntry = ZipEntry(entryName)
            addEntry.method = ZipEntry.DEFLATED
            output.putNextEntry(addEntry)

            FileInputStream(file).use {
                BufferedInputStream(it).use { bufferedInputStream ->
                    val bytes = ByteArray(1024)
                    var count = bufferedInputStream.read(bytes)
                    while(count != -1){
                        output.write(bytes,0,count)
                        count = bufferedInputStream.read(bytes)
                    }
                }
            }
            output.closeEntry()



        }
    }

    if (this.delete()) {
        if (!dest.renameTo(this)) {
            dest.copyTo(this, true)
        }
    }

    //修改新增添的ZipEntry method 为 ZipEntry.STORED
    if(ZipEntry.STORED == entryMethod){
        dest = File.createTempFile(SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP, SdkConstants.DOT_RES)
        ZipOutputStream(dest.outputStream()).use { output ->
            ZipFile(this).use { zip ->
                zip.entries().asSequence().forEach { origin ->
                    val target = ZipEntry(origin.name).apply {
                        size = origin.size
                        crc = origin.crc
                        comment = origin.comment
                        extra = origin.extra
                        method =  if(origin.name == entryName) ZipEntry.STORED else origin.method
                    }

                    output.putNextEntry(target)
                    zip.getInputStream(origin).use {
                        it.copyTo(output)
                    }
                    output.closeEntry()

                }

            }
        }

        if (this.delete()) {
            if (!dest.renameTo(this)) {
                dest.copyTo(this, true)
            }
        }
    }


}

private fun File.printZipEntry(entryName: String){
    ZipFile(this).use { zip ->
        zip.entries().asSequence().forEach { origin ->
            if(origin.name.contains(entryName)){
                println("${origin.name} ,crc = ${origin.crc},method = ${origin.method}")
                val target = ZipEntry(origin.name).apply {
                    size = origin.size
                    crc = origin.crc
                    comment = origin.comment
                    extra = origin.extra
                    method =  origin.method
                }

            }


        }
    }
}

private fun File.findDuplicatedResources():Map<Key,ArrayList<DuplicatedEntry>>{
    var duplicatedResources = HashMap<Key,ArrayList<DuplicatedEntry>>(100)
    ZipFile(this).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val lastIndex : Int = entry.name.lastIndexOf('/')
            val key = Key(entry.crc.toString(),if(lastIndex == -1) "/" else entry.name.substring(0,lastIndex))
            if(!duplicatedResources.containsKey(key)){
                val list : ArrayList<DuplicatedEntry> = ArrayList(20)
                duplicatedResources[key] = list
            }

            val list = duplicatedResources[key]
            list?.add(DuplicatedEntry(entry.name,entry.size,entry.compressedSize))

        }
    }

    duplicatedResources?.filter {
        it.value.size >= 2
    }.apply{
        duplicatedResources = this as HashMap<Key, ArrayList<DuplicatedEntry>>
    }




    return duplicatedResources
}

private fun File.printDuplicatedResources(){
    val duplicatedResources = findDuplicatedResources()

    duplicatedResources.forEach { mapEntry ->
        if(mapEntry.value.size > 0){
            mapEntry.value.forEach{
                println("${mapEntry.key}: size = ${mapEntry.value.size} ,name = ${it.name}")
            }
        }


    }


}

data class DuplicatedEntry(val name:String,val size:Long,val compressionSize:Long)

data class ExtractZipEntryResult(val entryName: String,var entryMethod: Int = ZipEntry.DEFLATED,var success :Boolean = false)

data class Key(val crc :String,val resourceDir :String){
    private var hashCode : Int

    init {
        hashCode = 0
    }

    override fun equals(other: Any?): Boolean {
        if(this === other){
            return true
        }

        if(other !is Key){
            return false
        }

        return crc == other.crc && resourceDir == other.resourceDir


    }

    override fun hashCode(): Int {
        if(hashCode == 0){
            hashCode = crc.hashCode()
            hashCode = 31 * hashCode + resourceDir.hashCode()
        }
        return hashCode;
    }

    override fun toString(): String {
        return "($crc,$resourceDir)"
    }
}


internal typealias RemoveRepeatResourceResults = CopyOnWriteArrayList<DuplicatedEntry>