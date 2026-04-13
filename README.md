# Linbox-WinEmu

<p align="center">
    <img src="ic_launcher-playstore.png" width="220" height="220" alt="Linbox Logo" />
</p>

## About

Linbox-WinEmu (formerly WinEmulator) is a Linux environment emulator for Android devices. It uses PRoot, Box64/86, and Termux-X11 to create a full Linux container on your phone or tablet, enabling you to run Windows applications and games. The app supports custom resolutions, multiple input modes, Wine compatibility layer configuration, and DXVK/VKD3D graphics acceleration — suitable for productivity, entertainment, and gaming.

This project was started by ewt45 and is actively maintained by afeimod.

<p align="center">
    <table>
        <tr>
            <td><img src="test1.png" width="800" height="620" alt="Screenshot 1" /></td>
            <td><img src="test2.png" width="800" height="620" alt="Screenshot 2" /></td>
        </tr>
    </table>
</p>


## Core Features

### PRoot Container Environment

The app uses PRoot technology to run a full Linux container on Android without root. You can switch between different Linux distribution rootfs images (Debian, Ubuntu, Arch Linux, etc.). Each container has its own locale, shared folders, and startup commands.

### X11 Display Server

Integrates Termux-X11 as an X11 server. Supports custom resolutions (800×600 up to 1920×1080, plus custom input). Three touch modes are available: virtual touchpad (precise cursor control), simulated touch (tap to click, long-press for right-click), and touchscreen (direct touch input).

### Wine Compatibility Layer

Supports installing and managing multiple Wine versions including wine-ge-custom and wine-tkg. Built-in support for DXVK, DXVK-ASYNC, DXVK-GPLASYNC (DirectX 9/10/11 → Vulkan), VKD3D (Direct3D 12 → Vulkan), and D8VK (Direct3D 8 → Vulkan) for significantly improved Windows game graphics performance.

### Box64/86 Emulator

Built-in Box64 and Box86 dynamic binary translators allow ARM64/Android devices to run x86_64 and x86 Linux and Windows programs, achieving broad software compatibility on mobile hardware.

### Input Control System

A full virtual input control system for creating and managing multiple control profiles. Each profile can include virtual buttons, virtual joysticks, mouse zones, and more. Profiles can be imported and exported for sharing and backup.

## Quick Start Guide

### Prerequisites

Before using Linbox-WinEmu, complete the following steps:

1. Go to phone Settings → Developer Options.
2. Enable **USB Debugging** and **USB Install**.
3. Find **Background Process Limit** or **Restrict background child processes** and set it to **No limit** / **Don't restrict background processes**. This is critical — without it, the app may crash with error 9.

If the app requests storage permission, grant **Manage All Files** access so it can read and write the file system.

### Creating Your First Container

After launching the app for the first time, create a Linux container:

After first launching the app, you need to create a Linux container. On the main screen, double-tap **Container Management** (available in two types: wine and fexwine) and tap **New Container**.

Once the container is created, select your newly created container as the active container.

### Starting the Environment and Running Programs

1. Return to the main screen and tap **Start**.
2. The app will launch the PRoot container and X11 server.
3. Once started, the X11 display fills the screen.
4. Use the integrated terminal to run Linux commands.
5. To run a Windows program, enter the Wine command in the terminal — e.g., `wine notepad.exe` for Notepad or `winecfg` for Wine configuration.

## Settings

### General Settings

**Resolution**: In X11 Display Settings → Resolution, choose a preset (800×600, 1024×768, 1280×720, 1600×900, 1920×1080) or enter a custom value. Higher resolutions produce sharper images but demand more GPU power. Default: 1280×720.

**Container Locale**: Sets the `LANG` environment variable on startup. Choose `zh_CN.utf8` (Simplified Chinese) or `en_US.utf8` (English).

**Shared Folders**: Add Android folders to make them accessible inside the Linux container. The Downloads folder is shared by default. Shared folders are mounted at their configured paths inside the container.

**Rootfs Switch**: Manage multiple Linux containers — add, delete, rename, or switch between them. A running container cannot be deleted. Changes take effect after restarting the app.

### X11 Display Settings

**Touch Mode**: Three modes available:
- **Virtual Touchpad** — bottom area of screen acts as a touchpad; single finger moves cursor; tap = left click; one finger hold + another finger tap = right click; two-finger move = scroll.
- **Simulated Touch** — tap = mouse click; long-press = right click.
- **Touchscreen** — screen acts as a direct touch device.

**Screen Orientation**: Lock to landscape, portrait, reverse landscape, reverse portrait, or follow system auto-rotate. Does not affect the system UI orientation.

**Display Scale**: Slider from 30% to 300%. Decrease scale to show more content on large/high-resolution screens; increase to zoom in.

**Keep Screen On**: Prevents auto-sleep while X11 is running. Useful for long gaming sessions or video playback.

### PRoot Settings

**PRoot Options**: Available flags include `-L` (symlink emulation), `--link2symlink` (required), `--kill-on-exit` (clean up processes on exit), `--sysvipc` (System V IPC support), and `--ashmem-memfd` (ashmem memory file descriptors). Keep defaults unless you know what each option does.

**Post-Start Command**: Commands entered here run automatically after container startup. Useful for setting environment variables, starting services, or launching a specific program. Example: `cd /usr/local/games && wine game.exe`.

### Input Control Settings

**Create a Profile**: Go to Input Controls → Add Profile, name it, then tap Edit Controls.

**Add Controls**: Tap an empty area to add a new control. Types include: button (bindable to keyboard keys or mouse actions), mouse zone (cursor follows drag), joystick, and scroll wheel.

**Bind Actions**: Select a control → tap Binding → press a button or move a stick to capture the input signal and bind it.

**Adjust Style**: Assign icons, shapes, colors, and opacity to controls. Lower opacity keeps controls from obscuring game content.

**Import/Export**: Export your control profile to a file for backup or use on another device.

## FAQ

### App crashes immediately on launch (Error 9)

Android is restricting background processes. Fix: Settings → Developer Options → Background Process Limit / Restrict background child processes → set to **No limit**. On some brands this is under Battery Management or App Background Management.

### Can't install rootfs or it won't start after installing

Check that storage space is sufficient — each rootfs needs at least 2–4 GB. Make sure the app has **Manage All Files** permission. If the network is unstable, try using a VPN or a better connection.

### No audio

Check whether PulseAudio is running inside the container: `pulseaudio --check`. If audio is choppy, try adding `-pa` to the PRoot launch command or start PulseAudio manually.

### Unresponsive touch or cursor drift

Switch touch modes in X11 Display Settings. Virtual Touchpad mode is best for precision. If using Simulated Touch mode, adjust the cursor speed setting.

### Can't access shared folders inside the container

Verify the shared folder path is correct and that the app has permission to access it on the Android side. Run `ls /storage/emulated/0/` in the terminal to confirm the shared directory is mounted.

## Architecture and Dependencies

### Core Stack

**Jetpack Compose** — Modern Android UI framework. https://developer.android.com/jetpack/compose

**Termux-X11** — X11 server for Android. https://github.com/termux/termux-x11

**PRoot** — Rootless Linux container technology. https://github.com/proot-me/PRoot

**PulseAudio** — Audio server for Linux applications and Wine. https://gitlab.freedesktop.org/pulseaudio/pulseaudio

### Compatibility Layers and Emulators

**Wine** — Windows API compatibility layer. https://www.winehq.org/

**Box64** — Dynamic binary translator for x86_64 on ARM64. https://github.com/ptitSeb/box64

**Box86** — Dynamic binary translator for x86 on ARM. https://github.com/ptitSeb/box86

**DXVK** — DirectX 9/10/11 → Vulkan translation layer. https://github.com/doitsujin/dxvk

**DXVK-ASYNC** — DXVK with async shader compilation. https://github.com/Sporif/dxvk-async

**DXVK-GPLASYNC** — DXVK GPL async variant. https://gitlab.com/Ph42oN/dxvk-gplasync

**VKD3D** — Direct3D 12 → Vulkan translation layer. https://github.com/lutris/vkd3d

**D8VK** — Direct3D 8 → Vulkan translation layer. https://github.com/AlpyneDreams/d8vk

### Reference Projects

- **Winlator** — Wine container solution for Android. https://github.com/brunodev85/winlator
- **Termux** — Android terminal emulator. https://github.com/termux/termux-app
- **PRoot-Distro** — PRoot distribution manager. https://github.com/proot-me/PRoot-Distro
- **Mobox** — Box64/86 integration for Android. https://github.com/olegos2/mobox

## Developer Guide

### Building the Project

Requirements: Android Studio (latest), Android SDK 35, JDK 11+. Clone the repository, open it in Android Studio, wait for Gradle sync, then run on a connected device or emulator.

### Directory Structure

Source code is under `app/src/main/java/org/github/ewt45/winemulator/`:
- `emu/` — PRoot container management, X11 service, audio service
- `ui/` — Compose UI screens; `setting/` — settings panels
- `inputcontrols/` — virtual input control system
- `viewmodel/` — MVVM ViewModel layer

### Adding a New Rootfs

Place the rootfs image in `assets/` and register the distribution in the codebase. See the existing Alpine rootfs configuration as a reference.

## Credits

### Contributors

Thanks to: ewt45 (project founder), afeimod (primary maintainer), hostei33, and all other contributors and Mobox developers.

### Open Source Dependencies

- **glibc-packages** — Termux glibc package repository. https://github.com/termux-pacman/glibc-packages
- **Mesa** — Open source graphics driver library. https://docs.mesa3d.org/
- **wine-ge-custom** — GloriousEggroll optimized Wine build. https://github.com/GloriousEggroll/wine-ge-custom
- **wine-tkg** — Custom Wine build toolkit. https://github.com/Frogging-Family/wine-tkg-git
- **wine-wayland** — Wine with Wayland support. https://github.com/Kron4ek/wine-wayland
- **wine-tkg (Kron4ek)** — Kron4ek's Wine TKG builds. https://github.com/Kron4ek/wine-tkg
- **Valve Wine** — Valve's optimized Wine. https://github.com/ValveSoftware/wine
- **wine-wayland (Collabora)** — Collabora's Wayland Wine fork. https://gitlab.collabora.com/alf/wine
- **wine-termux** — Wine optimized for Termux. https://github.com/Waim908/wine-termux
- **mesa-zink** — Mesa Zink renderer. https://github.com/alexvorxx/mesa-zink-11.06.22

## Download and Feedback

Download the latest APK from the [GitHub Releases](../../releases) page. For bug reports or feature requests, open an issue on [GitHub Issues](../../issues).

---

*Linbox-WinEmu is distributed under the GPL v3 open source license.*
