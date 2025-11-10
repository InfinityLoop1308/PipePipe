# PipePipe+ Implementation Guide

**Complete Technical Specification for Building the Universal YouTube/MX Hybrid Player**

---

## 📋 Table of Contents

1. [Project Overview](#project-overview)
2. [Prerequisites](#prerequisites)
3. [Phase 1: Project Setup](#phase-1-project-setup)
4. [Phase 2: Core Player Engine](#phase-2-core-player-engine)
5. [Phase 3: External Player Support](#phase-3-external-player-support)
6. [Phase 4: Offline Media Library](#phase-4-offline-media-library)
7. [Phase 5: YouTube UI Clone](#phase-5-youtube-ui-clone)
8. [Phase 6: Advanced Features](#phase-6-advanced-features)
9. [Phase 7: Polish & Testing](#phase-7-polish--testing)
10. [Appendix: Code Snippets](#appendix-code-snippets)

---

## Project Overview

**PipePipe+** transforms the existing PipePipe app into a universal video/music player by:

- Cloning YouTube Android app UI/UX for online content
- Adding MX Playerstyle offline/local media capabilities
- Enabling system-wide external player functionality
- Maintaining all existing PipePipe features (SponsorBlock, ReturnYouTubeDislike, etc.)

**Target Devices**: ARM64 Android phones (Poco X5 Pro and similar)  
**Development Environment**: Android-only (AIDE, Termux, no PC required)  
**Base Project**: https://github.com/osphvdhwj/PipePipe  
**License**: GPL-3.0

---

## Prerequisites

### Required Tools (Android)

1. **AIDE**  Kotlin/Java IDE
2. **Termux**  Command-line access, Git, Gradle
3. **Spck Editor or DroidEdit**  XML layout editing
4. **GitHub App**  Repository management

### Required Knowledge

- Kotlin programming
- Android development basics (Activities, Fragments, Services)
- ExoPlayer API
- Material Design 3
- Android intents and content providers

### Existing PipePipe Features to Preserve

- YouTube/NicoNico/BiliBili support
- SponsorBlock integration
- ReturnYouTubeDislike
- Login functionality
- Danmaku/live chat overlays
- Advanced search filters
- Background playback
- Download manager
- Local playlists

---

## P