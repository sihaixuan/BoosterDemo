package com.sihaixuan.booster.task.compression

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.didiglobal.booster.gradle.processResTask
import com.didiglobal.booster.gradle.processedRes
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.gradle.scope
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.kotlinx.touch


import com.didiglobal.booster.task.spi.VariantProcessor
import com.didiglobal.booster.util.search
import com.google.auto.service.AutoService
import org.apache.tools.ant.taskdefs.Zip
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
 *
 * Represents a variant processor for resources compression, the running order graph shows as below:
 *
 * ```
 *                      +--------------------+
 *                     | packageAndroidTask |
 *                     +--------+----------+
 *                              |
 *                   +-----------------------+
 *                  | removeUnusedResources |
 *                  +-----------+----------+
 *                              |
 *                    +---------------------+
 *                   | shrinkResourcesTask |
 *                  +-----------+---------+
 *                              |
 *                    +-----------------------+
 *                   | removeRepeatResources |
 *                  +-----------+-----------+
 *                             |
 *                   +---------------------+
 *                  |    processResTask   |
 *                 +-----------+---------+
 *
 *
 */
@AutoService(VariantProcessor::class)
class RemoveRepeatResourceVariantProcessor: VariantProcessor  {
    override fun process(variant: BaseVariant) {

//        variant.shrinkResourcesTask?.inputs?.files?.forEach {
//            println("shrinkResourcesTask inputs files: ${it.absolutePath}")
//        }
//
//        variant.shrinkResourcesTask?.outputs?.files?.forEach {
//            println("shrinkResourcesTask output files: ${it.absolutePath}")
//        }
//
//        variant.packageAndroidTask.inputs.files.forEach {
//            println("packageAndroidTask input files: ${it.absolutePath}")
//        }
//
//        variant.packageAndroidTask.outputs.files.forEach {
//            println("packageAndroidTask out files: ${it.absolutePath}")
//        }

        val results = RemoveRepeatResourceResults()

        //重复资源的筛选条件为 资源的zipEntry.crc相等，最先出现的资源压缩包产物是在processResTask，
        // 尽可能早的删除重复资源，可以减少后续task的执行时间
        variant.processResTask.doLast{
            variant.removeRepeatResources(it.logger,results)
        }

        variant.packageAndroidTask.doFirst{
            //开启了shrinkResources，才能知道无用资源哪些
            variant.shrinkResourcesTask?.apply {
                variant.removeUnusedResources(it.logger,results)
            }

            variant.generateReport(results)

        }

    }
}

/**
 * unused webp,jpg crc = 0,size = 0
 * from https://android.googlesource.com/platform/tools/base/+/refs/tags/gradle_3.1.2/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/ResourceUsageAnalyzer.java
 * method : replaceWithDummyEntry
 */

val unusedResourceCrcs  = longArrayOf(
    ResourceUsageAnalyzer.TINY_PNG_CRC,
    ResourceUsageAnalyzer.TINY_9PNG_CRC,
    ResourceUsageAnalyzer.TINY_BINARY_XML_CRC,
    ResourceUsageAnalyzer.TINY_PROTO_XML_CRC,
    0 //jpg、jpeg、webp等
)


/**
 *
 * Generates report with format like the following:
 *
 * deleted or unused or shrink | DuplicatedOrUnusedEntryType  | zipRetry name | size | compressed size
 *
 */
private fun BaseVariant.generateReport(results: RemoveRepeatResourceResults) {
    var totalSize : Long= 0
    var duplicatedSize : Long = 0
    var unusedSize : Long = 0
    var arscSize : Long = 0

    val maxWidth0 = results.map { it.name.length }.max() ?: 0
    val maxWidth1 = (results.map { it.size.toString().length }.max() ?: 0) + 6
    val maxWidth2 = (results.map { it.compressionSize.toString().length }.max() ?: 0) + 6
    val maxWidth3 = (results.map { it.entryType.length}.max() ?: 0) + 6

    var fullWidth = 0

    project.buildDir.file("reports", "RemoveRepeatResource", name, "report.txt").touch().printWriter().use{ fileLogger ->
        results.forEach { entry ->
            var text = "deleted ${entry.entryType.padEnd(maxWidth3)} ${entry.name.padEnd(maxWidth0)} ${entry.size.toString().padStart(maxWidth1)} ${entry.compressionSize.toString().padStart(maxWidth2)}"
            if(entry.entryType == DuplicatedOrUnusedEntryType.asrc){
                text = "shrink  ${entry.entryType.padEnd(maxWidth3)} ${entry.name.padEnd(maxWidth0)} ${entry.size.toString().padStart(maxWidth1)} ${entry.compressionSize.toString().padStart(maxWidth2)}"

            }
            println(text)
            fileLogger.println(text)

            if(fullWidth == 0){
                fullWidth = text.length
            }

            if(entry.entryType != DuplicatedOrUnusedEntryType.asrc){
                totalSize += if(entry.size == entry.compressionSize) entry.size else entry.compressionSize
            }else{
                totalSize += if(entry.extral == null) 0 else entry.extral as Long
            }

            when(entry.entryType){
                DuplicatedOrUnusedEntryType.duplicated  ->  duplicatedSize += if(entry.size == entry.compressionSize) entry.size else entry.compressionSize
                DuplicatedOrUnusedEntryType.unused      ->  unusedSize += if(entry.size == entry.compressionSize) entry.size else entry.compressionSize
                else  -> arscSize += if(entry.extral == null) 0 else entry.extral as Long
            }


        }

        val text = "all deleted size : ${String.format("%.2f",(totalSize / 1024.0))} kb ," +
                "duplicatedSize : ${String.format("%.2f",(duplicatedSize / 1024.0))} kb , " +
                "unusedSize : ${String.format("%.2f",(unusedSize / 1024.0))} kb" +
                (if(arscSize == 0L) "" else " , resources.arsc shrinked size : ${String.format("%.2f",(arscSize / 1024.0))} kb")

        if(fullWidth > 0){
            if(fullWidth < text.length){
                fullWidth = text.length
            }
            val content= "-".repeat(fullWidth)
            println(content)
            fileLogger.println(content)

        }
        println(text)
        fileLogger.println(text)


    }




}

private fun BaseVariant.removeUnusedResources(logger:Logger,results:RemoveRepeatResourceResults){
    packageAndroidTask.inputs.files.files.apply {
        val files = search{
            it.name.startsWith(SdkConstants.FN_RES_BASE) && it.extension == SdkConstants.EXT_RES && it.name.contains("stripped")
        }
        files.parallelStream().forEach { ap_ ->
            doRemoveUnusedResources(ap_,logger,results)
        }
    }

}

private fun doRemoveUnusedResources(apFile:File,logger:Logger,results:RemoveRepeatResourceResults){


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

        //找出未使用的资源
        val unusedResources = apFile.findUnusedResources()

        if(unusedResources.isEmpty()){
            logger.warn("${apFile.name} $entryName do not has unusedResources")
            return
        }



        //通过android-chunk-utils修改resources.arsc，把未使用的资源从对应的stringPool中删除
        FileInputStream(arscFile).use {
            ResourceFile.fromInputStream(it).apply {
                val chucks = this.chunks
                unusedResources.forEach { entry ->

                    apFile.removeZipEntry(entry.name)

                    chucks.filter { chunk ->
                        chunk is ResourceTableChunk
                    }.map { chunk ->
                        chunk as ResourceTableChunk
                    }.forEach { chunk ->
                        val stringPoolChunk = chunk.stringPool
                        val strings = stringPoolChunk.getStrings()
                        strings?.apply {
                            val iterator = this.listIterator() as MutableListIterator
                            while(iterator.hasNext()){
                                val value = iterator.next()
                                if(value == entry.name){
                                    iterator.remove()
                                }
                            }
                        }
                    }

                }


                results.addAll(unusedResources)

                arscFile.delete()

                //把ResourceFile 中数据，写入到arscFile去
                FileOutputStream(arscFile).use {
                    BufferedOutputStream(it).use { bufferedOutputStream ->
                        bufferedOutputStream.write(this.toByteArray())
                    }
                }

                //从resources.ap_压缩包中删除resources.arsc条目
                apFile.removeZipEntry(entryName)

                //把修改后的resources.arsc文件,打入resources.ap_压缩包
                apFile.addZipEntry(arscFile, entryName, extractResult.entryMethod)


            }

        }
        if(!arscFile.delete()){
            logger.error("fail to delete the file `$arscFile`")
        }

        //获取修改后的ap_文件中resources.arsc的信息,添加
        val entry = apFile.getZipEntry(entryName)
        entry?.apply {
            val duplicatedOrUnusedEntry = DuplicatedOrUnusedEntry(entryName,entry.size,entry.compressedSize,
                DuplicatedOrUnusedEntryType.asrc,(extractResult.compressionSize - entry.compressedSize ))
            results.add(duplicatedOrUnusedEntry)
        }

    }catch (e:Exception){
        logger.error("doRemoveUnusedResources", e)
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

        //找出没使用的资源资源
        val duplicatedResources = apFile.findDuplicatedResources()

        if(duplicatedResources.isEmpty()){
            logger.error("${apFile.name} $entryName do not has duplicatedResources")
            return
        }


        //根据找到的重复资源集合，保留resources.ap_压缩重复的资源的第一个ZipEntry,其他重复ZipEntry删除
        //通过android-chunk-utils修改resources.arsc，把这些重复的资源都重定向到同一个文件上；
        FileInputStream(arscFile).use{
            ResourceFile.fromInputStream(it).apply {
                val chucks = this.chunks
                val toBeReplacedResourceMap = HashMap<String,String>(1024)
                val removedDuplicatedEntries = ArrayList<DuplicatedOrUnusedEntry>()

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

                //把修改后的resources.arsc文件,打入resources.ap_压缩包
                apFile.addZipEntry(arscFile,entryName,extractResult.entryMethod)

            }

        }
        if(!arscFile.delete()){
            logger.error("fail to delete the file `$arscFile`")
        }
    }catch (e:Exception){
        logger.error("doRemoveRepeatResources happen error", e)
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

private fun StringPoolChunk.getStrings():List<String>?{
    try{
        val field = javaClass.getDeclaredField("strings")
        field.setAccessible(true)
        return field.get(this) as List<String>
    }catch (e:Exception){
        e.printStackTrace()
    }

    return null

}


private fun File.getZipEntry(entryName:String):ZipEntry?{
    var zipEntry  = ZipEntry(entryName)

    ZipFile(this).use { zip ->
        zip.entries().asSequence().run outside@{
            forEach inside@ {
                if(it.name == entryName){
                    zipEntry.crc = it.crc
                    zipEntry.size = it.size
                    zipEntry.compressedSize = it.compressedSize
                }
            }
        }
    }

    if(zipEntry.size == 0L){
        return null
    }

    return zipEntry
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
                result.size = targetEntry.size
                result.compressionSize = targetEntry.size

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


private fun File.findUnusedResources():List<DuplicatedOrUnusedEntry>{
    var unusedResources = ArrayList<DuplicatedOrUnusedEntry>(100)
    ZipFile(this).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            if(entry.crc in unusedResourceCrcs){
                unusedResources.add(DuplicatedOrUnusedEntry(entry.name,entry.size,entry.compressedSize,DuplicatedOrUnusedEntryType.unused))
            }
        }

    }

    return unusedResources
}


private fun File.findDuplicatedResources():Map<Key,ArrayList<DuplicatedOrUnusedEntry>>{
    var duplicatedResources = HashMap<Key,ArrayList<DuplicatedOrUnusedEntry>>(100)
    ZipFile(this).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val lastIndex : Int = entry.name.lastIndexOf('/')
            val key = Key(entry.crc.toString(),if(lastIndex == -1) "/" else entry.name.substring(0,lastIndex))
            if(!duplicatedResources.containsKey(key)){
                val list : ArrayList<DuplicatedOrUnusedEntry> = ArrayList(20)
                duplicatedResources[key] = list
            }

            val list = duplicatedResources[key]
            list?.add(DuplicatedOrUnusedEntry(entry.name,entry.size,entry.compressedSize,DuplicatedOrUnusedEntryType.duplicated))

        }
    }

    duplicatedResources.filter {
        it.value.size >= 2
    }.apply{
        duplicatedResources = this as HashMap<Key, ArrayList<DuplicatedOrUnusedEntry>>
    }

    return duplicatedResources
}


private class DuplicatedOrUnusedEntryType{
    companion object {
        const val duplicated = "duplicated"
        const val unused = "unused"
        const val asrc = "resources.arsc"
    }
}

data class DuplicatedOrUnusedEntry(val name:String,val size:Long,val compressionSize:Long,val entryType:String,var extral : Any? = null)

data class ExtractZipEntryResult(val entryName: String,var entryMethod: Int = ZipEntry.DEFLATED,var size: Long = 0,var compressionSize: Long = 0,var success :Boolean = false)

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


internal typealias RemoveRepeatResourceResults = CopyOnWriteArrayList<DuplicatedOrUnusedEntry>