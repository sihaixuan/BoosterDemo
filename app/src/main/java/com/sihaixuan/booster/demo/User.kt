package com.sihaixuan.booster.demo

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/5/31 20:51
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */
class User(var name:String){
    companion object{
        @JvmField
        val DEFAULT = User("default")
        const val NAME = "default"
        const val age = 233233
    }
}