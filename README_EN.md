# FloatBro

[English](README_EN.md) | [简体中文](README.md)

> A lightweight, modern floating multitasking browser for Android.

[![Latest Release](https://img.shields.io/github/v/release/gxwane/floatbro?include_prereleases&color=orange&label=Latest%20Release)](https://github.com/gxwane/floatbro/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/gxwane/floatbro/pr-checks.yml?branch=main&label=Build%20Status)](https://github.com/gxwane/floatbro/actions)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android MinSdk](https://img.shields.io/badge/minSdk-26-green.svg)](https://developer.android.com/about/versions/oreo)
[![TargetSdk](https://img.shields.io/badge/targetSdk-36-brightgreen.svg)](https://developer.android.com)

---

## 📸 Showcase

| 🪟 Floating Window | 🪟 Multi-Window | 🫧 Edge Bubble |
| :---: | :---: | :---: |
| ![FloatBro Window](docs/images/floatbro_window.png) | ![FloatBro Multi](docs/images/floatbro_multi.png) | ![FloatBro Bubble](docs/images/floatbro_bubble.png) |

---

## ✨ Features

* **System-Wide Floating Window**: Browse the web freely on top of any app without full-screen browser interruption. Tear tabs off from the main app directly into floating overlays.
* **Concurrent Multi-Window & Bubbles**: Run multiple independent floating browser windows simultaneously, or pin multiple bubbles along the screen edge.
* **Fluid Resize Handle**: Drag the bottom-right corner handle to smoothly adjust window dimensions and aspect ratio.
* **Edge Snap, Collapse & Quick Dismiss**: Push a window against the screen edge to collapse it into a floating bubble, and tap to instantly restore. Drag down to the bottom target to quickly dismiss.
* **Fullscreen Video Controls**: Floating controls and resize handle remain accessible when playing HTML5 videos in fullscreen, allowing repositioning and resizing anytime.
* **Built-in Ad Blocking**: Fast filter rule matching and CSS element hiding for a clean, distraction-free browsing experience.
* **Modern Material 3 Design**: Complete tab management, reactive history sync, dynamic dark/light themes, and full compatibility with Android 15 16KB memory page size.

---

## 💡 Gestures Guide

| Action | Gesture | Result |
| :--- | :--- | :--- |
| **Resize Window** | Drag bottom-right corner handle | Freely adjust window size and aspect ratio |
| **Collapse to Bubble** | Push window past screen edge | Collapses into an edge bubble to clear screen space |
| **Restore Window** | Tap the edge bubble | Expands bubble back into a floating window |
| **Dismiss Window** | Drag window toward bottom of screen | Releases onto the dismiss target to close |
| **Fullscreen Video Control** | When web video enters fullscreen | Centered top capsule pill appears for dragging and exiting fullscreen |

---

## 📥 Download

* Download the latest APK release from **[GitHub Releases](https://github.com/gxwane/floatbro/releases)**.
* **Requirements**: Android 8.0 (API 26) or higher (compatible with Android 15 & 16).

---

## 🛠️ Building from Source

### Requirements
* **JDK**: 17
* **Android SDK**: CompileSdk 36, TargetSdk 36, MinSdk 26
* **Android NDK**: 27.0.12077973 (r27)
* **CMake**: 3.22.1+

### Build Commands
```bash
git clone https://github.com/gxwane/floatbro.git
cd floatbro

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
# Output path: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ Architecture

```text
FloatBro/
├── app/                  # Application host, LauncherActivity, FloatBrowserService, floating windows & bubbles
├── core/
│   ├── webview/          # EBWebView engine wrapper, soft-keyboard focus coordination
│   └── common/           # Material 3 tokens, display metrics & preferences persistence
├── ad-filter/            # EasyList parsing, CSS injection & interception pipeline
├── adblock-client/       # C++ rule matching engine (libadblock-client.so)
└── docs/                 # Architecture specifications, technical design & showcase assets
```

---

## ⚖️ License & Acknowledgements

* **License**: FloatBro is licensed under the **[GNU General Public License v3.0 (GPL-3.0-or-later)](LICENSE)**.
* **Acknowledgements**: FloatBro's ad-blocking core is inspired by and adapted from the open-source E-ink browser **[EinkBro](https://github.com/plateaukao/einkbro)**. Special thanks to original author **[Plateau Kao](https://github.com/plateaukao)** and the EinkBro community for their exceptional work.
