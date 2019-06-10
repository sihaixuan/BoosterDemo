package com.sihaixuan.booster.task.compression

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.didiglobal.booster.gradle.project

/**
 *
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/8 0:27
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 * @version   1.0
 *
 */
val BaseVariant.packageAndroidTask: PackageAndroidArtifact
    get() = project.tasks.withType(PackageAndroidArtifact::class.java).findByName("package${name.capitalize()}")!!