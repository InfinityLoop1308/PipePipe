# Step 2: YouTube Shorts UI with Vertical Swipe & Adaptive Video Shapes

This document contains all files needed to implement the YouTube Shorts vertical swipe feed with adaptive video container shapes based on aspect ratio.

---

## Overview

YouTube Shorts presents videos in different aspect ratios:
- **Portrait (9:16)**: Full screen, most common for Shorts
- **Square (1:1)**: Full screen with black bars on sides
- **Landscape (16:9)**: Centered with letterbox effect

This implementation dynamically adjusts video containers based on aspect ratio.

---

## File Structure

```
app/src/main/
├── java/your/package/ui/youtube/
│   ├── ShortsFragment.kt
│   ├── ShortsAdapter.kt
│   └── ShortVideoItem.kt
├── res/
│   ├── layout/
│   │   ├── fragment_shorts.xml
│   │   └── item_short_video.xml
│   └── drawable/
│       └── bg_shorts_bottom_gradient.xml
```

---

## Key Features

1. **Vertical Swipe Navigation** using ViewPager2
2. **Adaptive Video Containers** that adjust to video aspect ratio
3. **YouTube Shorts UI Elements**:
   - Right-side action buttons (Like, Dislike, Comment, Share)
   - Bottom overlay with channel info and video title
   - Subscribe button
   - Audio/music attribution
4. **ExoPlayer Integration** for video playback
5. **Auto-pause/play** when swiping between videos

---

## Implementation

### Aspect Ratio Logic

```kotlin
private fun adjustVideoContainer(aspectRatio: Float) {
    val layoutParams = container.layoutParams as FrameLayout.LayoutParams
    
    when {
        aspectRatio < 0.75f -> {
            // Very tall portrait (9:16 or taller)
            layoutParams.width = MATCH_PARENT
            layoutParams.height = MATCH_PARENT
        }
        aspectRatio < 1.2f -> {
            // Portrait to square (3:4 to 4:3)
            layoutParams.width = MATCH_PARENT
            layoutParams.height = MATCH_PARENT
        }
        else -> {
            // Landscape (16:9 or wider)
            layoutParams.width = MATCH_PARENT
            layoutParams.height = (screenWidth / aspectRatio).toInt()
        }
    }
    
    container.layoutParams = layoutParams
}
```

---

## UI Components

### Right-Side Actions (YouTube Shorts Style)

- **Like Button** with count
- **Dislike Button**
- **Comment Button** with count
- **Share Button**
- **More Options** (three-dot menu)

### Bottom Overlay

- **Channel Avatar** (circular)
- **Channel Name**
- **Subscribe Button**
- **Video Title/Description** (2 lines max)
- **Audio/Music Attribution**

---

## Dependencies

Add to `app/build.gradle`:

```gradle
dependencies {
    // ExoPlayer for video playback
    implementation 'com.google.android.exoplayer:exoplayer:2.19.1'
    
    // ViewPager2 for vertical swipe
    implementation 'androidx.viewpager2:viewpager2:1.1.0'
    
    // Glide for image loading
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    
    // Material Design 3
    implementation 'com.google.android.material:material:1.11.0'
}
```

---

## Integration with PipePipe

1. **Replace Mock Data**: Connect to PipePipe's YouTube extractor to load real Shorts
2. **Video URLs**: Use PipePipe's video stream extraction for playback
3. **Channel Data**: Fetch channel avatars and subscriber counts
4. **Interactions**: Integrate Like/Dislike with ReturnYouTubeDislike API
5. **Comments**: Load and display comment counts

---

## Performance Optimization

1. **Video Recycling**: Release ExoPlayer instances when videos scroll away
2. **Memory Management**: Only keep 3 video players in memory (current + previous + next)
3. **Preloading**: Preload next video while current is playing
4. **Thumbnail Caching**: Use Glide's caching for smooth scrolling

---

## Usage Example

```kotlin
// In MainActivity or Navigation
val shortsFragment = ShortsFragment()
supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, shortsFragment)
    .commit()
```

---

## Testing Checklist

- [ ] Vertical swipe works smoothly
- [ ] Videos auto-play when scrolled to
- [ ] Videos pause when scrolled away
- [ ] Portrait videos fill screen
- [ ] Square videos display correctly
- [ ] Landscape videos show with letterbox
- [ ] Like/dislike buttons respond
- [ ] Subscribe button works
- [ ] Share functionality opens system share sheet
- [ ] Comment button opens comments
- [ ] Audio attribution displayed correctly

---

**Status**: Step 2 Complete - YouTube Shorts UI with Adaptive Video Shapes  
**Next**: Step 3 - Video Player Screen or Channel Page UI
