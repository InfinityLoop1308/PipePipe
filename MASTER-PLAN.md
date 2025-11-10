# PipePipe+ Master Plan
## NextGen YouTube/MX Hybrid Player - Complete Implementation Guide

**A fully open-source universal video/music player that combines YouTube's UI with MX Player's features.**

---

## 🎯 Project Vision

PipePipe+ aims to deliver one universal app that:

- 🎥 **Looks exactly like YouTube Android app** (UI/UX clone)
- 🎧 **Works with YouTube online** and **offline/local media** (music + video)
- 🧩 **Adds all missing player-level features from MX Player** (gestures, formats, resume, etc.)
- ⚙️ **Works as external player for all apps** (`Open with…`)
- 🚀 **Fully open-source** (so others can build upon it)
- 📱 **Can be developed directly on Android** (no PC required)

---

## 📋 Table of Contents

1. [Core Goals](#1-core-goals)
2. [Architecture Overview](#2-architecture-overview)
3. [Core Modules](#3-core-modules)
4. [UI/UX Implementation](#4-uiux-implementation)
5. [Feature Implementation](#5-feature-implementation)
6. [Integration Flow](#6-integration-flow)
7. [Development Workflow](#7-development-workflow)
8. [Testing & Deployment](#8-testing--deployment)
9. [License & Credits](#9-license--credits)

---

## 1. Core Goals

| Feature                      | Description                                                                |
|-----------------------------|----------------------------------------------------------------------------|
| **YouTube Clone UI**         | Same design, navigation, and transitions as YouTube official app           |
| **Offline Mode**             | Local media browser (MX Player–style) seamlessly inside same app           |
| **External Player Support**  | Acts as system-wide external player for video/audio                        |
| **MX Player Features**       | Gestures (volume/brightness), equalizer, subtitles, playback speed, resume |
| **PIP (Picture-in-Picture)** | Continue watching while using other apps                                   |
| **Background Audio**         | Keep playing YouTube music/video with screen off                           |
| **History & Watch Later**    | Save progress and lists locally                                            |
| **Dynamic Theme**            | Adaptive light/dark theme matching YouTube                                 |
| **Shorts & Channels**        | Same UI & animations as YouTube's "Shorts" and "Channel" tabs              |

---

## 2. Architecture Overview

### Project Structure

```
app/
 ├── core/
 │    ├── player/          # Unified ExoPlayer engine
 │    ├── extractor/       # YouTube & other service parsers
 │    ├── repository/      # Video/Audio/History/Download storage
 │    └── utils/           # Helpers, themes, gesture logic
 ├── ui/
 │    ├── youtube/         # Home, Shorts, Channel, VideoPlayer
 │    ├── offline/         # LocalLibrary, MusicPlayer
 │    ├── player/          # Common player controls
 │    ├── components/      # Bottom bar, tabs, dialogs
 │    └── settings/        # App settings, themes
 ├── res/                  # XML layouts, themes, icons
 ├── build.gradle
 └── AndroidManifest.xml
```

### Technology Stack

| Component           | Technology                    |
|--------------------|-------------------------------|
| **Language**        | Kotlin                        |
| **Player Engine**   | ExoPlayer 3                   |
| **UI Framework**    | Material Design 3             |
| **Navigation**      | ViewPager2, BottomNavigation  |
| **Image Loading**   | Glide                         |
| **Database**        | Room (optional for history)   |
| **Build System**    | Gradle                        |
| **Min SDK**         | 21 (Android 5.0)              |
| **Target SDK**      | 34 (Android 14)               |

---

## 3. Core Modules

### 3.1 Unified Player Engine

**File**: `core/player/UnifiedPlayer.kt`

```kotlin
object UnifiedPlayer {
    private var exoPlayer: ExoPlayer? = null
    
    fun initialize(context: Context) {
        exoPlayer = ExoPlayer.Builder(context).build()
    }
    
    fun play(uri: Uri, startPosition: Long = 0) {
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            seekTo(startPosition)
            play()
        }
    }
    
    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    fun setPlaybackSpeed(speed: Float) { exoPlayer?.setPlaybackSpeed(speed) }
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration ?: 0L
    fun release() { exoPlayer?.release(); exoPlayer = null }
    fun getPlayer(): ExoPlayer? = exoPlayer
}
```

**Features**:
- Single shared instance for all playback modes
- Supports all formats (MP4, MKV, MP3, FLAC, etc.)
- Handles playback speed, subtitles, audio track switching
- Resume from last position

---

### 3.2 YouTube Extractor

**File**: `core/extractor/YouTubeExtractor.kt`

**Note**: Already exists in PipePipe - extend it to fetch:
- Shorts feed
- Channel info  
- Recommended videos
- Trending content

---

### 3.3 Gesture Controller

**File**: `core/player/GestureController.kt`

Handles MX Player–style gestures:
- **Horizontal swipe**: Seek forward/backward
- **Left vertical swipe**: Brightness control
- **Right vertical swipe**: Volume control
- **Double tap**: Play/pause
- **Long press**: Speed control

---

### 3.4 Playback State Manager

**File**: `core/player/PlaybackStateManager.kt`

Manages:
- Last played position for each video
- Playback speed preference
- Resume points
- Watch history

Uses SharedPreferences or Room DB for persistence.

---

## 4. UI/UX Implementation

### Complete UI Components (6 Steps)

All UI implementation guides are available in the `docs/` folder:

#### **Step 1: YouTube Home Screen**
- **File**: `docs/UI-STEP1-HOME.md`
- **Components**: Video feed, chip filters, toolbar, RecyclerView
- **Key Files**: 
  - `fragment_youtube_home.xml`
  - `YouTubeHomeFragment.kt`
  - `VideoCardAdapter.kt`

#### **Step 2: YouTube Shorts**
- **File**: `docs/UI-STEP2-SHORTS.md`
- **Components**: Vertical swipe, adaptive video shapes (9:16, 1:1, 16:9)
- **Key Files**:
  - `fragment_shorts.xml`
  - `ShortsFragment.kt`
  - `ShortsAdapter.kt`

#### **Step 3: Video Player**
- **File**: `docs/UI-STEP3-PLAYER.md`
- **Components**: Full-screen player, bottom sheet, mini-player
- **Key Files**:
  - `activity_video_player.xml`
  - `VideoPlayerActivity.kt`
  - `view_mini_player.xml`

#### **Step 4: Bottom Navigation**
- **File**: `docs/UI-STEP4-NAVIGATION.md`
- **Components**: 5-tab navigation (Home, Shorts, Add, Subscriptions, Library)
- **Key Files**:
  - `activity_main.xml`
  - `MainActivity.kt`
  - `bottom_navigation_menu.xml`

#### **Step 5: Channel Page**
- **File**: `docs/UI-STEP5-CHANNEL.md`
- **Components**: Channel header, tabs, video grid, collapsing toolbar
- **Key Files**:
  - `activity_channel.xml`
  - `ChannelActivity.kt`
  - `ChannelPagerAdapter.kt`

#### **Step 6: Search Interface**
- **File**: `docs/UI-STEP6-SEARCH.md`
- **Components**: Search bar, filters, suggestions, voice search
- **Key Files**:
  - `activity_search.xml`
  - `SearchActivity.kt`
  - `SearchResultsAdapter.kt`

---

## ... (TRUNCATED for brevity) ...
