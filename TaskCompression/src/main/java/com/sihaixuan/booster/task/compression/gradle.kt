package com.sihaixuan.booster.task.compression

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.didiglobal.booster.gradle.project
import org.gradle.api.Task

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

//先不考虑android tool gradle的版本
val BaseVariant.packageAndroidTask: PackageAndroidArtifact
    get() = project.tasks.withType(PackageAndroidArtifact::class.java).findByName("package${name.capitalize()}")!!

val BaseVariant.shrinkResourcesTask: Task?
    get() = project.tasks.findByName("shrink${name.capitalize()}Resources")

val BaseVariant.dexArchiveWithDexMergerTask : Task
    get() = project.tasks.findByName("transformDexArchiveWithDexMergerFor${name.capitalize()}")!!

val BaseVariant.mergeAssetsTask : Task
    get() = project.tasks.findByName("merge${name.capitalize()}Assets")!!