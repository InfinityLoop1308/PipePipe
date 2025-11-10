<p align="center"><img src="assets/logo.png" width="150"></p> 
<h2 align="center"><b>PipePipe+</b></h2>
<h4 align="center">
NewPipe, reimagined: faster, more stable, and packed with more features.</h4>
<p align="center"><a href="https://f-droid.org/packages/InfinityLoop1309.NewPipeEnhanced/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid"  width="207" /></a>
<a href="https://apt.izzysoft.de/fdroid/index/apk/InfinityLoop1309.NewPipeEnhanced"><img src="assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="207" /></a></p>

---

## 🚀 PipePipe+ Enhancement Plan

**PipePipe+** extends the original PipePipe with a universal player experience that combines:

- **YouTube Android App UI Clone**  Exact look and feel for online video experience
- **MX Player1style Offline Mode**  Local media browsing with advanced player controls
- **System-Wide External Player**  Works as "Open with" for all apps
- **Picture-in-Picture & Background Audio**  Continue playing while multitasking
- **Built Entirely on Android**  No PC required for development or testing

###  New Features in PipePipe+

| Feature                      | Description                                                                |
|------------------------------|----------------------------------------------------------------------------|
| **YouTube Clone UI**         | Bottom navigation, Shorts feed, channel pages matching YouTube exactly     |
| **Offline Media Library**    | MX Player1style local video/music browser with folder navigation           |
| **External Player Support**  | System-wide intent handler for all video/audio file types                  |
| **Advanced Gestures**        | Volume/brightness swipe, seek gestures, long-press speed control           |
| **Resume Playback**          | Auto-resume from last position for all content                             |
| **Enhanced Subtitles**       | Support for all subtitle formats with customizable styling                 |
| **Audio Equalizer**          | Built-in equalizer for music playback                                      |
| **Mini Player**              | Bottom sheet mini-player when navigating away                              |
| **Dynamic Themes**           | Material You adaptive theming with YouTube-style aesthetics                |

###  Extended Architecture

```
app/
    core/
           player/          # Unified ExoPlayer engine (online + offline)
           extractor/       # YouTube, NicoNico, BiliBili parsers
           repository/      # Video/Audio/History/Download storage
           utils/           # Gesture handlers, themes, helpers
    ui/
           youtube/         # YouTube clone: Home, Shorts, Channel, Player
           offline/         # MX-style: LocalLibrary, MusicPlayer
           player/          # Unified player controls (online/offline)
           components/      # Bottom nav, tabs, dialogs, mini-player
           settings/        # App settings, themes, preferences
    res/                  # Layouts, themes, icons, drawables
    build.gradle
    AndroidManifest.xml   # Intent filters for external player
```

### 🎨 UI/UX Design Principles

- **YouTube Parity**: Bottom navigation tabs, card layouts, Shorts vertical swipe
- **MX Player Controls**: Gesture overlays, floating speed/subtitle controls
- **Material You**: Adaptive light/dark themes, Roboto fonts
- **Seamless Transitions**: One unified experience across online/offline modes

### 🔌 Integration Flow

| Source          | UI Mode              | Player Mode             | Features                   |
|----------------|----------------------|-------------------------|----------------------------|
| YouTube         | `ui/youtube/*`       | UnifiedPlayer (online)  | Feed, Shorts, Channels, PIP|
| Local Videos    | `ui/offline/*`       | UnifiedPlayer (offline) | Gestures, Resume, Formats  |
| Local Music     | `ui/offline/*`       | UnifiedPlayer (audio)   | Background, EQ, Mini-player|
| External Intent | `ExternalPlayerActivity`| UnifiedPlayer        | PIP, Subtitles, All Formats|

---

## Beyond NewPipe

#### YouTube Enhancements
* Integrate SponsorBlock for skipping sponsored segments (YouTube & BiliBili) 
* Restore YouTube dislikes with ReturnYouTubeDislike 
* Show original titles on YouTube (non-localized) 
* Log in to access restricted or premium content 

#### Media Features
* Display live chats in danmaku-style overlays
* Support AV1 and VP9 codecs for efficient, high-quality playback 
* Enable music player mode with background playback 

#### Filtering
* Apply advanced search filters for better discovery 
* Filter out unwanted items by keywords or channels 
* Block shorts and paid videos for a cleaner feed 

#### Playback Controls
* Use swipe-to-seek and fullscreen gestures for intuitive navigation 
* Long-press to speed up playback 
* Set a sleep timer for bedtime listening 

#### Enhanced Playlists
* Download full playlists at once 
* Search and sort within local playlists and histories

... and many more improvements!


## Screenshots

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/00-v2.png" width=640>](fastlane/metadata/android/en-US/images/phoneScreenshots/00-v1.png)

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/01-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/02-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/03-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/04-v3.png)
<br/>
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/05-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/06-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/07-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/08-v3.png)
<br/>
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/09-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/10-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/11-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/12-v3.png)


## About this fork

Due to differences in development philosophy, I forked NewPipe in early 2022 and began independent development based on it.

This means that PipePipe neither receives updates from NewPipe nor pushes updates to NewPipe. They have become two separate projects. Issues that occur in NewPipe don't necessarily happen in PipePipe, and changes made in NewPipe may not be adopted by PipePipe. In contrast, forks like Tubular track the latest version of NewPipe and develop based on it.

Making a hard fork allows us to effectively address issues with quick fixes and maintain frequent feature updates.

**PipePipe+ continues this independent development philosophy**, adding MX Player1style offline capabilities and system-wide external player support while maintaining all existing PipePipe features.

## About sign in

PipePipe will ONLY use the login cookie for the specified scenarios you set. You can configure it in "Cookie Functions."

For YouTube, the cookie will only be used when retrieving playback streams.

##  Development (Android-Only Workflow)

PipePipe+ is designed to be built and tested entirely on Android devices without requiring a PC:

| Tool                   | Purpose                                      |
|-----------------------|----------------------------------------------|
| **AIDE**              | Full Kotlin/Java IDE on Android              |
| **Spck Editor**       | XML layout editing                           |
| **Termux**            | Command-line tools, Git, Gradle builds       |
| **GitHub App**        | Repository management and pull requests      |

### Build Instructions

1. Clone the repository using Termux or GitHub app
2. Open project in AIDE
3. Run `./gradlew assembleDebug` in Termux
4. Install and test the APK on your device

## Contribute

Issues and PRs are welcomed. Please note that I will **NOT** accept service requests. 

Anyone interested in creating their own service is encouraged to fork this repository.

For PipePipe+ specific features (offline mode, external player, etc.), please label your issues/PRs with `[PipePipe+]`.

## Getting Nightly Builds

Visit https://nightly.pipepipe.dev to download the latest nightly builds. These give you access to the most recent updates and fixes before they're included in the next official release.

## Donation

If you find PipePipe useful, please consider becoming a supporter on Ko-Fi. Your support is important to me and helps me add more exciting new features. Every bit counts! 


Liberapay: https://liberapay.com/PipePipe

Ko-fi: https://ko-fi.com/pipepipe

## Special Thanks

[SocialSisterYi/bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) for providing some BiliBili API lists.

[AioiLight](https://github.com/AioiLight) for providing some code of NicoNico service.
