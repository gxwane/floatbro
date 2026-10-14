package io.github.floatbro.architecture

import android.app.Activity
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.BeforeClass
import org.junit.Test

/**
 * FloatBro 架构防腐与稳定性守护断言测试套件 (Phase 3 Quality Engineering)
 *
 * 专门针对系统悬浮窗 (TYPE_APPLICATION_OVERLAY) 与后台 Service 运行环境的特殊性进行高信噪比断言：
 * 1. 悬浮窗上下文隔离：严禁在 Overlay / Service 中持有 Activity 引用，杜绝内存泄漏；
 * 2. 悬浮窗防崩溃：严禁在 Overlay 视图中使用系统级 Dialog / PopupMenu，杜绝 BadTokenException；
 * 3. 分层单向依赖：底层 common 绝不反向依赖上层 overlay/ui；
 * 4. 线程调度规约：严禁裸线程 Thread，统一由协程管理。
 */
class ArchitectureRuleTest {
    companion object {
        private lateinit var importedClasses: com.tngtech.archunit.core.domain.JavaClasses

        @BeforeClass
        @JvmStatic
        fun setUp() {
            importedClasses =
                ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("io.github.floatbro")
        }
    }

    /**
     * 铁律 1: 悬浮窗与服务绝对不允许持有 Activity 字段成员（防止 Context 泄漏）
     * 排除 service.trampoline 包（用于文件选择与权限请求的合法透明代理 Activity）。
     */
    @Test
    fun overlayAndServiceMustNotHoldActivityFields() {
        fields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..io.github.floatbro.overlay..", "..io.github.floatbro.service..")
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..io.github.floatbro.service.trampoline..")
            .should()
            .notHaveRawType(Activity::class.java)
            .check(importedClasses)
    }

    /**
     * 铁律 2: 悬浮窗视图体系严禁使用系统级 Dialog / PopupMenu（杜绝 BadTokenException 崩溃）
     * 悬浮窗窗口通过 Application Overlay 挂载，无 Activity Window Token，直接调用系统 Dialog 必崩。
     */
    @Test
    fun overlayMustNotUseSystemDialogs() {
        noClasses()
            .that()
            .resideInAPackage("..io.github.floatbro.overlay..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "android.app.Dialog",
                "android.app.AlertDialog",
                "androidx.appcompat.app.AlertDialog",
                "android.widget.PopupMenu",
            ).check(importedClasses)
    }

    /**
     * 铁律 3: 架构分层单向依赖，core.common 绝不允许反向依赖上层 overlay 或 ui
     */
    @Test
    fun coreCommonMustNotDependOnUpperLayers() {
        noClasses()
            .that()
            .resideInAPackage("..io.github.floatbro.core.common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..io.github.floatbro.overlay..", "..io.github.floatbro.ui..")
            .check(importedClasses)
    }

    /**
     * 铁律 4: 严禁在业务层直接创建裸线程 java.lang.Thread，强制统一使用 Kotlin 协程调度
     */
    @Test
    fun domainCodeMustNotInstantiateRawThreads() {
        noClasses()
            .that()
            .resideInAnyPackage("..io.github.floatbro.overlay..", "..io.github.floatbro.browser..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.lang.Thread")
            .check(importedClasses)
    }
}
