# 🚀 FloatBro v0.1.3-beta

FloatBro v0.1.3-beta 带来了**100% 纯净无遮挡的悬浮窗全屏视频观影体验**，并全面修复了**在线检查更新 404** 与**设置页关于版本显示不同步**的问题。

---

### 🌟 本次关键功能与修复 (Changelog)

1. 🎬 **悬浮视频全屏纯净沉浸 (Pure Fullscreen Video)**：
   - 彻底移除全屏视频上方常驻的半透明胶囊顶条（`videoFullscreenBar`），消灭对画面标题、字幕和人物面部的遮挡，呈现 100% 满屏原汁原味的视频体验；
   - 边框手势优化：进入全屏后支持通过**顶边与左右侧边**（16dp）拖拽平滑移动悬浮窗位置，底部边缘不拦截，彻底避开播放器进度条（Seek Bar）；
   - 保留右下角 36dp 缩放手柄，全屏视频下依然支持随心动态缩放窗口尺寸；
   - 完备的退出逃生通道：依托播放器原生退出全屏按钮及系统返回键（最高优先级拦截），退出路径平滑顺畅。

2. 🔄 **在线检查更新与版本对齐 (App Update & Version Sync)**：
   - 修复在线检查更新 GitHub Releases 仓库所有者指向 `gxwane/floatbro`，彻底解决 0.1.1-beta / 0.1.2-beta 检查更新查不到升级版本的问题；
   - 设置页「关于」卡片动态绑定 `BuildConfig.VERSION_NAME`，与应用实际构建版本时刻保持严格一致。

---

> 欢迎在 [GitHub Issues](https://github.com/gxwane/floatbro/issues) 或 [GitHub Discussions](https://github.com/gxwane/floatbro/discussions) 反馈交流您的宝贵体验！
