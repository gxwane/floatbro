# 变更日志 (Changelog)

本项目所有显著变更均记录于此文件。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 初始化 FloatBro 独立多模块工程骨架（`:app`, `:core:webview`, `:core:common`, `:ad-filter`, `:adblock-client`）。
- 落地基于 `gradle/libs.versions.toml` 的 Version Catalog 统一依赖管理体系（Kotlin 2.1.20, AGP 8.13.2, CompileSdk 36, TargetSdk 36, JDK 17）。
- 引入官方 GPL-3.0 许可证与 NOTICE 溯源致敬声明。
- 落地工业级 GitHub Actions CI/CD 流水线（PR 自动审查、Release 自动签名打包、16KB Page Size 阻断校验）。
