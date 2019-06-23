package com.sihaixuan.booster.task.compression

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.didiglobal.booster.gradle.*
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.kotlinx.touch


import com.didiglobal.booster.task.spi.VariantProcessor
import com.didiglobal.booster.util.search
import com.google.auto.service.AutoService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sihaixuan.extractzip.util.removeString
import com.sihaixuan.extractzip.util.setString
import org.gradle.api.Project
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.logging.Logger
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
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
 *                                      +--------------------+
 *                                     | packageAndroidTask |
 *                                     +--------+----------+
 *                                        /             \
 *                                      /                \
 *                   +-----------------------+        +----------------------------------+
 *                  | removeUnusedResources |        |    removeUnusedAssetsResources   |
 *                  +-----------+----------+        +----------------------------------+
 *                              |                                 /                \
 *                    +---------------------+             +-----------------+   +-----------------------------------+
 *                   | shrinkResourcesTask |             | mergeAssetsTask  |  |  transformDexArchiveWithDexMerger |
 *                  +-----------+---------+             +------------------+   +-----------------------------------+
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





        val transformDexArchiveWithDexMerger = variant.project.tasks.findByName("transformDexArchiveWithDexMergerFor${variant.name.capitalize()}")

        transformDexArchiveWithDexMerger?.apply {
            println("***$name")
            outputs?.files?.forEach {
                println("***output file: ${it.absolutePath}")
            }
        }


        println()

        val mergeAssetsTask = variant.project.tasks.findByName("merge${variant.name.capitalize()}Assets")

        mergeAssetsTask!!.apply {
            println("***$name")
            outputs.files.forEach {
                println("*** output file: ${it.absolutePath}")
            }

        }

//        val packageAndroidTask =   variant.packageAndroidTask
//        packageAndroidTask.inputs.files.forEach {
//            println("***${mergeAssetsTask.name} input file: ${it.absolutePath}")
//        }

        println("")

        val results = RemoveRepeatResourceResults()

        //重复资源的筛选条件为 资源的zipEntry.crc相等，最先出现的资源压缩包产物是在processResTask，
        // 尽可能早的删除重复资源，可以减少后续task的执行时间
        variant.processResTask.doLast{
            variant.removeRepeatResources(it.logger,results)
        }

        variant.packageAndroidTask.doFirst{
//            开启了shrinkResources，才能知道无用资源哪些
            variant.shrinkResourcesTask?.apply {
                variant.removeUnusedResources(it.logger,results)

            }

            //unusedAssetsResources优化
            variant.removeUnusedAssetsResources(it.logger,results)

            variant.generateReport(results)


        }
        

    }
}

private fun getIgnoreAssetsResources(project : Project):List<String>{
    val results = ArrayList<String>()

    val jsonFile = File(project.projectDir,"TaskCompression.json")
    if(!jsonFile.exists()){
        println("${project.name}.json do not exist!")
        return results
    }

    FileReader(jsonFile).use{
        val jsonObject = JsonParser().parse(it) as JsonObject
        val jsonArray = jsonObject.getAsJsonArray("ignoreAssets")
        jsonArray.forEach {
            results.add(it.asString)
        }
//        println("results = $results")
    }

    return results


}

private fun findUsedAssetsResources(variant: BaseVariant,assetsResources:List<String>): List<String>{
    val results = ArrayList<String>()

    var dexFiles = HashSet<File>()

    variant.dexArchiveWithDexMergerTask.outputs.files.files.apply {
        val files = search{
            it.extension == SdkConstants.EXT_DEX
        }
        dexFiles.addAll(files)
    }

    if(dexFiles.size == 0){
        println("${variant.name.capitalize()} findUsedAssetsResources : can not find dex file!")
        return results
    }

    dexFiles.forEach {
        try{
            val dexFile = DexFileFactory.loadDexFile(it, Opcodes.forApi(15))
            val options = BaksmaliOptions()
            dexFile.classes.parallelStream().forEach {
                it.disassembleClass(options)?.apply {
                    readSmaliLines(this,assetsResources,results)
                }
            }

        }catch (e :Exception){
            e.printStackTrace()
        }
    }

    return results
}


private fun readSmaliLines(lines:List<String>,assetsResources: List<String>,results:MutableList<String>){
    lines.forEach {line ->
        val newLine = line.trim()
        if(!newLine.isNullOrEmpty() && line.startsWith("const-string")){
            val columns = newLine.split(",")
            if(columns.size == 2){
                var assetFileName = columns[1].trim()
                assetFileName = assetFileName.substring(1, assetFileName.length - 1)
                if(!assetFileName.isNullOrEmpty() && assetFileName in assetsResources){
                    results.add(assetFileName)
                }
            }
        }
    }
}

private fun BaseVariant.removeUnusedAssetsResources(logger:Logger,results:RemoveRepeatResourceResults){
    doRemoveUnusedAssetsResources(this,logger,results)
}


private fun BaseVariant.findAssetsResource():List<String>{
    val results = ArrayList<String>()
    mergeAssetsTask.outputs.files.files.apply {
        val files = search {
            it.exists() && it.isFile
        }

        files.forEach {
            results.add(it.name)
        }
    }

    return results
}

private fun doRemoveUnusedAssetsResources(variant: BaseVariant,logger:Logger,results:RemoveRepeatResourceResults){


    try {


        //找出assets资源
        val assetsResources = variant.findAssetsResource()

        if (assetsResources.isEmpty()) {
            logger.warn("${variant.name.capitalize()} $ do not has assetsResources")
            return
        }

        //找出usedAssets
        val usedAssetsResource = findUsedAssetsResources(variant,assetsResources)

        if(usedAssetsResource.isEmpty()){
            logger.warn("${variant.name.capitalize()} usedAssetsResource is empty!")
        }

        //找出asserts白名单
        val ignoreAssetsResources = getIgnoreAssetsResources(variant.project)

        //找出unusedAssets
        var unusedAssetsResources = ArrayList<String>()
        assetsResources.forEach assets@{ asset ->

            if(asset in ignoreAssetsResources){
                return@assets
            }

            usedAssetsResource.forEach { usedAsset ->
                if (asset.endsWith(usedAsset)) {
                    return@assets
                }
            }
            unusedAssetsResources.add(asset)
        }

        if (unusedAssetsResources.isEmpty()) {
            logger.warn("${variant.name.capitalize()}  $ do not has unusedAssetsResources")
            return
        }

        unusedAssetsResources.forEach {
            logger.warn("unusedAssetsResources :$it")
        }

        //删除unusedAssets
        variant.mergeAssetsTask.outputs.files.files.apply {
            val suffix = "incremental${File.separator}merge${variant.name.capitalize()}Assets${File.separator}merger.xml"
            val files = search {
                it.exists() && it.isFile && !it.absolutePath.endsWith(suffix)
            }

            files.forEach {
                if(it.name in unusedAssetsResources){
                    val size = it.length()
                    if(it.delete()){
                        val name = "assets/" + it.name
                        results.add(DuplicatedOrUnusedEntry(-1L,name,size,size,DuplicatedOrUnusedEntryType.unusedAssets))

                    }else{
                        logger.warn("${variant.name.capitalize()} failed to  delete ${it.absolutePath} ")
                    }
                }
            }

        }


    }catch (e :Exception){
        logger.error("${variant.name.capitalize()} doRemoveUnusedAssetsResources happen error ",e)
    }







}




/**
 * unusedResource webp,jpg crc = 0,size = 0
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
 * deleted or unusedResource or shrink | DuplicatedOrUnusedEntryType  | zipRetry name | size | compressed size
 *
 */
private fun BaseVariant.generateReport(results: RemoveRepeatResourceResults) {
    var totalSize : Long= 0
    var duplicatedSize : Long = 0
    var unusedResourcesSize : Long = 0
    var unusedAssetsSize : Long = 0
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
                DuplicatedOrUnusedEntryType.unusedResource      ->  unusedResourcesSize += if(entry.size == entry.compressionSize) entry.size else entry.compressionSize
                DuplicatedOrUnusedEntryType.unusedAssets    ->  unusedAssetsSize += if(entry.size == entry.compressionSize) entry.size else entry.compressionSize
                else  -> arscSize += if(entry.extral == null) 0 else entry.extral as Long
            }


        }

        val text = "all deleted size : ${String.format("%.2f",(totalSize / 1024.0))} kb ," +
                "duplicatedSize : ${String.format("%.2f",(duplicatedSize / 1024.0))} kb , " +
                "unusedResourcesSize : ${String.format("%.2f",(unusedResourcesSize / 1024.0))} kb, " +
                "unusedAssetsSize : ${String.format("%.2f",(unusedAssetsSize / 1024.0))} kb"
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
//            doRemoveUnusedResources(ap_,logger,results)
            doRemoveUnusedResources1(ap_,logger,results)
        }
    }

}


/**
 * 没有未使用资源的crc 有五类 见unusedResourceCrcs
 * 每类无使用资源当做重复资源优化处理
 */
private fun doRemoveUnusedResources1(apFile:File,logger:Logger,results:RemoveRepeatResourceResults){


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



        //通过android-chunk-utils修改resources.arsc，把未使用的资源按照crc分类，按照重复资源优化处理
        FileInputStream(arscFile).use {
            ResourceFile.fromInputStream(it).apply {
                val chucks = this.chunks
                val toBeReplacedResourceMap = HashMap<String,String>()
                val removedDuplicatedResources =  ArrayList<DuplicatedOrUnusedEntry>()

                unusedResources.groupBy {
                    it.crc
                }.filter {
                    it.value.size > 1
                }.forEach { mapEntry ->
                    mapEntry.value.apply {
                        val retained = mapEntry.value[0]
                        val removedEntryNames = ArrayList<String>()

                        forEach { entry ->
                            if(retained != entry){
                                // 保留第一个资源 删除重复的资源
                                removedEntryNames.add(entry.name)
                                removedDuplicatedResources.add(entry)
                                toBeReplacedResourceMap[entry.name] = retained.name
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






                results.addAll(removedDuplicatedResources)

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
            val duplicatedOrUnusedEntry = DuplicatedOrUnusedEntry(entry.crc,entryName,entry.size,entry.compressedSize,
                DuplicatedOrUnusedEntryType.asrc,(extractResult.compressionSize - entry.compressedSize ))
            results.add(duplicatedOrUnusedEntry)
        }

    }catch (e:Exception){
        logger.error("doRemoveUnusedResources", e)
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
                        chunk.removeString(entry.name)
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
            val duplicatedOrUnusedEntry = DuplicatedOrUnusedEntry(entry.crc,entryName,entry.size,entry.compressedSize,
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
                unusedResources.add(DuplicatedOrUnusedEntry(entry.crc,entry.name,entry.size,entry.compressedSize,DuplicatedOrUnusedEntryType.unusedResource))
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
            list?.add(DuplicatedOrUnusedEntry(entry.crc,entry.name,entry.size,entry.compressedSize,DuplicatedOrUnusedEntryType.duplicated))

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
        const val unusedResource = "unusedResource"
        const val unusedAssets = "unusedAssets"
        const val asrc = "resources.arsc"

    }
}

data class DuplicatedOrUnusedEntry(val crc : Long,val name:String,val size:Long,val compressionSize:Long,val entryType:String,var extral : Any? = null)

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
        return hashCode
    }

    override fun toString(): String {
        return "($crc,$resourceDir)"
    }
}


internal typealias RemoveRepeatResourceResults = CopyOnWriteArrayList<DuplicatedOrUnusedEntry>