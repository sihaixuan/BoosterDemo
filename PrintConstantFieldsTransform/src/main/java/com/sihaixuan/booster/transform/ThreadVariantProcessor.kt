package com.sihaixuan.booster.transform


import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.gradle.scope
import com.didiglobal.booster.task.spi.VariantProcessor
import com.google.auto.service.AutoService

@AutoService(VariantProcessor::class)
class ThreadVariantProcessor : VariantProcessor {

    override fun process(variant: BaseVariant) {
        variant.scope.globalScope.project.dependencies.add("implementation", "com.didiglobal.booster:booster-android-instrument-thread:0.4.5")
    }

}
