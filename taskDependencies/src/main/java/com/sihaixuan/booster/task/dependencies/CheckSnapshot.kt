package com.sihaixuan.booster.task.dependencies

import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.gradle.dependencies
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.gradle.scope
import com.didiglobal.booster.kotlinx.*
import org.gradle.api.DefaultTask
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.PrintWriter

internal open class CheckSnapshot : DefaultTask() {

    lateinit var variant: BaseVariant
    private lateinit var logger: PrintWriter

    @TaskAction
    fun run() {
        var reportDir = File(variant.project.buildDir, "reports").also { it.mkdirs() }
        logger = reportDir.file("${this::class.java.simpleName}").file("report.txt").touch().printWriter()

        if (!variant.buildType.isDebuggable) {

        }

        variant.dependencies.filter {
            it.id.componentIdentifier is MavenUniqueSnapshotComponentIdentifier
        }.map {
            it.id.componentIdentifier as MavenUniqueSnapshotComponentIdentifier
        }.ifNotEmpty { snapshots ->
            println("$CSI_YELLOW ⚠️  ${snapshots.size} SNAPSHOT artifacts found in ${variant.name} variant:$CSI_RESET\n${snapshots.joinToString("\n") { snapshot -> "$CSI_YELLOW→  ${snapshot.displayName}$CSI_RESET" }}")
            logger.println("$CSI_YELLOW ⚠️  ${snapshots.size} SNAPSHOT artifacts found in ${variant.name} variant:$CSI_RESET\n${snapshots.joinToString("\n") { snapshot -> "$CSI_YELLOW→  ${snapshot.displayName}$CSI_RESET" }}")
        }.ifEmpty {
            logger.println("$CSI_YELLOW ⚠️ SNAPSHOTs is empty")

        }

        variant.dependencies.ifNotEmpty { snapshots->
            println("$CSI_YELLOW ⚠️  ${snapshots.size} SNAPSHOT artifacts found in ${variant.name} variant:$CSI_RESET\n${snapshots.joinToString("\n") { snapshot -> "$CSI_YELLOW→  ${snapshot.id.displayName}$CSI_RESET" }}")
            logger.println("$CSI_YELLOW ⚠️  ${snapshots.size} SNAPSHOT artifacts found in ${variant.name} variant:$CSI_RESET\n${snapshots.joinToString("\n") { snapshot -> "$CSI_YELLOW→  ${snapshot.id.displayName}$CSI_RESET" }}")
        }
    }

}
