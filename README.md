# CrestCode

**CrestCode is an Android code editor with an integrated Linux terminal.**

Built with native Android UI, Monaco Editor, and an Alpine Linux environment powered by PRoot, CrestCode aims to bring a practical coding workspace to Android devices.

> 🚧 CrestCode is under active development. Features and workflows may change as development progresses.

## ✨ Features

### 📝 Code Editor
- Monaco Editor integration.
- Syntax highlighting provided by Monaco.
- File and workspace handling.
- Keyboard shortcuts for common editor actions.
- Editing and save functionality under active development.

### 💻 Integrated Linux Terminal
- Alpine Linux userspace.
- PRoot-based execution environment.
- Terminal integrated into the Android application.
- Command-line workflow without requiring a separate terminal app for the integrated environment.

### 📱 Native Android Interface
- Built with Kotlin.
- Jetpack Compose-based UI.
- Native Android components combined with a WebView-based code editor.
- Designed for a mobile coding workflow.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Application development |
| Jetpack Compose | Native Android UI |
| WebView | Monaco Editor integration |
| Monaco Editor | Code editing |
| Alpine Linux | Linux userspace |
| PRoot | Userspace environment execution |
| Gradle | Build system |

## 📋 Requirements

- Android device running a compatible Android version.
- Android SDK and build tools for development.
- JDK compatible with the project's Gradle configuration.
- Sufficient storage for the application and Linux environment.

Exact Android version requirements and supported architectures depend on the current project configuration.

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/CrestCode.git
cd CrestCode
