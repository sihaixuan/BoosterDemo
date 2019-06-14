package com.sihaixuan.extractzip.util

import pink.madis.apk.arsc.*
import java.io.PrintWriter

fun TypeSpecChunk.getResources():IntArray?{
    try{
        val field = javaClass.getDeclaredField("resources")
        field.setAccessible(true)
        return field.get(this) as IntArray
    }catch (e:Exception){
        e.printStackTrace()
    }

    return null
}

fun StringPoolChunk.setString(index:Int, value:String){
    try{
        val field = javaClass.getDeclaredField("strings")
        field.setAccessible(true)
        val list = field.get(this) as MutableList<String>
        list.set(index,value)
    }catch (e:Exception){
        e.printStackTrace()
    }

}


fun StringPoolChunk.getStrings():List<String>?{
    try{
        val field = javaClass.getDeclaredField("strings")
        field.setAccessible(true)
        return field.get(this) as List<String>
    }catch (e:Exception){
        e.printStackTrace()
    }

    return null

}

fun StringPoolChunk.getStyles():MutableList<Any>?{
    try{
        val field = javaClass.getDeclaredField("styles")
        field.setAccessible(true)
        return field.get(this) as MutableList<Any>
    }catch (e:Exception){
        e.printStackTrace()
    }

    return null

}

fun TypeChunk.getMutableEntities():MutableMap<Int,TypeChunk.Entry>?{
    try{
        val field = javaClass.getDeclaredField("entries")
        field.setAccessible(true)
        return field.get(this) as MutableMap<Int,TypeChunk.Entry>
    }catch (e:Exception){
        e.printStackTrace()
    }

    return null
}

fun ResourceValue.setData(data: Int){
    try{
        val field = javaClass.getDeclaredField("data")
        field.setAccessible(true)
        field.setInt(this,data)
    }catch (e:Exception){
        e.printStackTrace()
    }
}

fun ResourceTableChunk.removeString(globalString:String,fileLogger : PrintWriter? = null){

    val stringPoolChunk = stringPool
    /**
     * These styles have a 1:1 relationship with the strings. For example, styles.get(3) refers to
     * the string at location strings.get(3). There are never more styles than strings (though there
     * may be less). Inside of that are all of the styles referenced by that string.
     */
    val strings = stringPoolChunk.getStrings()
    val styles = stringPoolChunk.getStyles()

    var deleteIndex = 0
    strings!!.apply {
        val iterator = this.listIterator() as MutableListIterator

        loop0@ while(iterator.hasNext()){
            val value = iterator.next()
            if(value == globalString){
                iterator.remove()
                break@loop0
            }

            if(styles != null && deleteIndex < styles.size){
                styles.removeAt(deleteIndex)
            }

            deleteIndex++

        }

        //删除全局 StringChunk的元素，后续的元素索引减一，需要改变对应的TypeChunk.Entry.ResourceValue string类型 data（对应全局StringChunk的索引）
        packages.forEach {packageChunk ->
            packageChunk.typeChunks.forEach { typeChunk ->
                typeChunk.getMutableEntities()!!.forEach{ mapEntry ->
                    var entry = mapEntry.value
                    if(entry.typeName() == "string"){
                        entry.value()?.apply {
                            if(data() > deleteIndex){
                                setData(data() - 1 )
                                println("reset TypeChunk entry resourceValue data ${this}")
                                fileLogger?.println("reset TypeChunk entry resourceValue data ${this}")
                            }

                        }

                        entry.values()?.forEach{ resourceValueEntry ->
                            resourceValueEntry.value?.apply {
                                if(type() == ResourceValue.Type.STRING){
                                    if(data() > deleteIndex){
                                        setData(data() - 1 )
                                        println("reset TypeChunk entry resourceValues data ${this}")
                                        fileLogger?.println("reset TypeChunk entry resourceValues data ${this}")
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }


}