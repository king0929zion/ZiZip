# ZiZip

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" height="120" alt="ZiZip Logo">
</p>

<p align="center">
  <strong>智能移动助手 - Auto-GLM Android 原生版</strong>
</p>

<p align="center">
  <a href="https://github.com/king0929zion/ZiZip/releases"><img src="https://img.shields.io/github/v/release/king0929zion/ZiZip?style=flat-square" alt="Release"></a>
  <a href="https://github.com/king0929zion/ZiZip/actions"><img src="https://img.shields.io/github/actions/workflow/status/king0929zion/ZiZip/android.yml?style=flat-square" alt="Build"></a>
  <img src="https://img.shields.io/badge/Android-8.0+-green?style=flat-square" alt="Android">
  <img src="https://img.shields.io/badge/Kotlin-1.9.20-blue?style=flat-square" alt="Kotlin">
</p>

---

## 📱 简介

ZiZip 是 Auto-GLM-Android Flutter 应用的原生 Android 版本，使用 Kotlin 和 Jetpack Compose 构建。

### ✨ 特性

- 🎨 **精美 UI**: Gemini 风格聊天界面，暖色调设计系统
- 🤖 **AI Agent**: 支持自动化任务执行
- ♿ **无障碍服务**: 屏幕操作和内容获取
- 🔲 **悬浮窗**: 任务状态悬浮显示
- 📱 **原生性能**: 纯 Kotlin + Compose 实现

---

## 📥 安装

### 从 Release 下载
前往 [Releases](https://github.com/king0929zion/ZiZip/releases) 下载最新 APK。

### 从源码构建
```bash
git clone https://github.com/king0929zion/ZiZip.git
cd ZiZip
./gradlew assembleDebug
```

---

## 🔧 权限要求

| 权限 | 用途 |
|-----|------|
| 无障碍服务 | 屏幕操作和内容读取 |
| 悬浮窗 | 显示任务状态窗口 |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────┐
│           UI Layer                  │
│  (Jetpack Compose + Navigation)     │
├─────────────────────────────────────┤
│         ViewModel Layer             │
│    (StateFlow + Coroutines)         │
├─────────────────────────────────────┤
│          Domain Layer               │
│   (ModelProvider Interface)         │
├─────────────────────────────────────┤
│           Data Layer                │
│  (Repository + SharedPreferences)   │
├─────────────────────────────────────┤
│         Service Layer               │
│ (Accessibility + Overlay Service)   │
└─────────────────────────────────────┘
```

---

## 📖 文档

详细开发文档请查看 [AGENTS.md](AGENTS.md)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

MIT License
