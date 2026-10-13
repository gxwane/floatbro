## 📌 变更说明 (Change Description)
<!-- 请简要总结本次 PR 的核心改动动机与实现方式 -->

- 关联 Issue: Fixes #<!-- 填入 Issue 编号 -->
- 变更类型:
  - [ ] 🚀 `feat`: 新增特性
  - [ ] 🐛 `fix`: 缺陷修复
  - [ ] ⚡ `perf`: 性能优化
  - [ ] ♻️ `refactor`: 代码重构
  - [ ] 🛠️ `build` / `ci`: 构建与自动化调整
  - [ ] 📝 `docs`: 文档变更

---

## 🔍 技术实现与改动要点 (Technical Highlights)
- <!-- 核心实现点 1: 例如在 FloatBrowserService 中接入软键盘监听动态更新 flag -->
- <!-- 核心实现点 2: 例如 TwoPaneLayout 增加拖拽分割条平滑重排 -->

---

## 📱 交互验证与效果展示 (Verification & Media)
<!-- 悬浮窗、分屏及手势类 PR 建议附带截图或动态演示 (GIF/MP4) -->

| 改动前 (Before) | 改动后 (After) |
| :---: | :---: |
| <!-- 图片/无 --> | <!-- 上传截图或 GIF --> |

---

## 📋 提交前自查清单 (Checklist)
- [ ] PR 标题遵循 Conventional Commits (如 `feat(split): support horizontal split mode`)。
- [ ] 本地通过构建与单元测试：`./gradlew assembleDebug` 与 `./gradlew testDebugUnitTest`。
- [ ] 新建源码文件已添加 GPL-3.0 SPDX 版权头；修改的文件保留了原作者版权。
- [ ] 若涉及功能增改或缺陷修复，已按规范同步更新 `CHANGELOG.md`。
- [ ] 无冗余代码、敏感凭据、硬编码绝对路径及临时测试 Log。
- [ ] 适配 Android 8.0 ~ 16 (API 26 ~ 36)（已确认悬浮窗权限、软键盘焦点及 16KB 内存分页兼容）。
