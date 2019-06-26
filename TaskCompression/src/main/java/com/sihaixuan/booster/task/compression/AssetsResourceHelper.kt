package com.sihaixuan.booster.task.compression

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.util.search
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import java.io.File
import java.io.FileReader

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/26 8:38
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */
class AssetsResourceHelper(private val variant : BaseVariant) {



    internal fun findAssetsResources() :List<String>{
        val results = ArrayList<String>()
        variant.mergeAssetsTask.outputs.files.files.apply {
            val files = search {
                it.exists() && it.isFile
            }

            val regex = "out" + File.separator
            files.forEach {
                //              println("findAssetsResource ${it.absolutePath}")
                if(it.absolutePath.contains(regex)){
                    val name = it.absolutePath.split(regex)[1].replace(File.separator,"/")
                    results.add(name)
                }

            }
        }

        return results
    }

    internal fun findUsedAssetsResources(assetsResources:List<String>) :List<String>{
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

        val constants = HashSet<String>()

        dexFiles.forEach {
            try{
                val dexFile = DexFileFactory.loadDexFile(it, Opcodes.forApi(15))
                val options = BaksmaliOptions()
                dexFile.classes.parallelStream().forEach {
                    it.disassembleClass(options)?.apply {
                        readSmaliLines(this,assetsResources,constants)
                    }
                }

            }catch (e :Exception){
                e.printStackTrace()
            }
        }

        if(results.isEmpty()){
            println("${variant.name.capitalize()} usedAssetsResource is empty!")
        }

        results.addAll(constants)
        constants.clear()
        return results
    }




    internal fun findIgnoreAssetsResources() :List<String>{
        val results = ArrayList<String>()

        val jsonFile = File(variant.project.projectDir,"TaskCompression.json")
        if(!jsonFile.exists()){
            println("TaskCompression.json do not exist!")
            return results
        }

        FileReader(jsonFile).use{
            val jsonObject = JsonParser().parse(it) as JsonObject
            val jsonArray = jsonObject.getAsJsonArray("ignoreAssets")
            jsonArray.forEach {
                results.add(StringUtil.globToRegexp(it.asString.replace(File.separator,"/")))
            }
//        println("results = $results")
        }

        return results
    }


    internal fun findUnusedAssetsResources(assetsRes :List<String>,usedAssetsRes:List<String>,ignoreAssetsRes : List<String>) :List<String>{
        var unusedAssetsRes = ArrayList<String>()
        assetsRes.forEach assets@{ asset ->


            if(ignoreAssetsRes.any { ignore -> asset.matches(Regex(ignore)) }){
                return@assets
            }

            usedAssetsRes.forEach { usedAsset ->
                if (asset.endsWith(usedAsset)) {
                    return@assets
                }
            }
            unusedAssetsRes.add(asset)
        }

        unusedAssetsRes.forEach {
            println("unusedAssetsResources :$it")
        }
        return unusedAssetsRes

    }


    private fun readSmaliLines(lines:List<String>,assetsResources: List<String>,results: HashSet<String>){
        lines.forEach {line ->
            val newLine = line.trim()
            if(!newLine.isNullOrEmpty() && newLine.startsWith("const-string")){
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


}

