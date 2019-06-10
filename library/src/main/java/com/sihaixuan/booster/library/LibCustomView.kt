package com.sihaixuan.booster.library

import android.content.Context
import android.util.AttributeSet
import android.view.View

import android.content.res.TypedArray



/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/1 8:54
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */
class LibCustomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {

    init{
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.LibCustomView, defStyleAttr, 0)
        var title  = a.getString(R.styleable.LibCustomView_title)
        var subTitle = a.getString(R.styleable.LibCustomView_subtitle)
        a.recycle()

    }
}