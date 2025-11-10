# Step 1: YouTube Home Screen UI Implementation

This document contains all files needed to implement the YouTube Home screen UI clone.

---

## File Structure

```
app/src/main/
├── java/your/package/ui/youtube/
│   ├── YouTubeHomeFragment.kt
│   ├── VideoCardAdapter.kt
│   └── VideoItem.kt
├── res/
│   ├── layout/
│   │   ├── fragment_youtube_home.xml
│   │   └── item_video_card.xml
│   ├── values/
│   │   ├── colors.xml
│   │   ├── strings.xml
│   │   └── dimens.xml
│   ├── drawable/
│   │   ├── bg_duration_badge.xml
│   │   └── (icons for toolbar)
│   └── font/
│       ├── roboto_regular.ttf
│       └── roboto_medium.ttf
```

---

## 1. Fragment Layout - `fragment_youtube_home.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface">

    <!-- App Bar -->
    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/app_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/colorSurface"
        app:elevation="0dp">

        <!-- Top Toolbar -->
        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:paddingStart="16dp"
            android:paddingEnd="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <!-- YouTube Logo -->
                <ImageView
                    android:id="@+id/youtube_logo"
                    android:layout_width="98dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_youtube_logo"
                    android:contentDescription="@string/youtube_logo" />

                <Space
                    android:layout_width="0dp"
                    android:layout_height="1dp"
                    android:layout_weight="1" />

                <!-- Cast Button -->
                <ImageButton
                    android:id="@+id/btn_cast"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@drawable/ic_cast"
                    android:contentDescription="@string/cast" />

                <!-- Notifications Button -->
                <ImageButton
                    android:id="@+id/btn_notifications"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@drawable/ic_notifications"
                    android:contentDescription="@string/notifications" />

                <!-- Search Button -->
                <ImageButton
                    android:id="@+id/btn_search"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@drawable/ic_search"
                    android:contentDescription="@string/search" />

                <!-- Profile Button -->
                <ImageView
                    android:id="@+id/btn_profile"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:layout_marginStart="8dp"
                    android:src="@drawable/ic_profile_placeholder"
                    android:contentDescription="@string/profile" />

            </LinearLayout>
        </androidx.appcompat.widget.Toolbar>

        <!-- Chip Filter Bar -->
        <HorizontalScrollView
            android:id="@+id/chip_scroll_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:scrollbars="none"
            android:paddingTop="8dp"
            android:paddingBottom="8dp">

            <com.google.android.material.chip.ChipGroup
                android:id="@+id/chip_group"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:paddingStart="12dp"
                android:paddingEnd="12dp"
                app:singleSelection="true"
                app:chipSpacingHorizontal="8dp">

                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_all"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="32dp"
                    android:text="@string/all"
                    android:checked="true" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_music"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="32dp"
                    android:text="@string/music" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_gaming"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="32dp"
                    android:text="@string/gaming" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_news"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="32dp"
                    android:text="@string/news" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_live"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="32dp"
                    android:text="@string/live" />

            </com.google.android.material.chip.ChipGroup>
        </HorizontalScrollView>

    </com.google.android.material.appbar.AppBarLayout>

    <!-- Video Feed RecyclerView -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/video_recycler_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingBottom="80dp"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

## 2. Video Card Layout - `item_video_card.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="16dp"
    app:cardElevation="0dp"
    app:cardCornerRadius="0dp"
    android:background="?attr/colorSurface">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <!-- Thumbnail -->
        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            app:layout_constraintDimensionRatio="16:9">

            <ImageView
                android:id="@+id/video_thumbnail"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:scaleType="centerCrop"
                android:background="@color/thumbnail_placeholder"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintBottom_toBottomOf="parent"
                android:contentDescription="@string/video_thumbnail" />

            <!-- Duration Badge -->
            <TextView
                android:id="@+id/video_duration"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_margin="4dp"
                android:paddingStart="4dp"
                android:paddingEnd="4dp"
                android:paddingTop="2dp"
                android:paddingBottom="2dp"
                android:background="@drawable/bg_duration_badge"
                android:textColor="@android:color/white"
                android:textSize="12sp"
                android:fontFamily="@font/roboto_medium"
                android:text="10:45"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintEnd_toEndOf="parent" />

        </androidx.constraintlayout.widget.ConstraintLayout>

        <!-- Video Info -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:padding="12dp">

            <!-- Channel Avatar -->
            <com.google.android.material.imageview.ShapeableImageView
                android:id="@+id/channel_avatar"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:scaleType="centerCrop"
                android:src="@drawable/ic_profile_placeholder"
                app:shapeAppearanceOverlay="@style/CircleImageView"
                android:contentDescription="@string/channel_avatar" />

            <!-- Title and Metadata -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginStart="12dp"
                android:orientation="vertical">

                <!-- Video Title -->
                <TextView
                    android:id="@+id/video_title"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Video Title Goes Here"
                    android:textColor="?attr/colorOnSurface"
                    android:textSize="14sp"
                    android:fontFamily="@font/roboto_medium"
                    android:maxLines="2"
                    android:ellipsize="end" />

                <!-- Channel Name and Stats -->
                <TextView
                    android:id="@+id/video_metadata"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="Channel Name • 1.2M views • 2 days ago"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:textSize="12sp"
                    android:fontFamily="@font/roboto_regular"
                    android:maxLines="1"
                    android:ellipsize="end" />

            </LinearLayout>

            <!-- More Options Menu -->
            <ImageButton
                android:id="@+id/btn_more"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@drawable/ic_more_vert"
                android:contentDescription="@string/more_options" />

        </LinearLayout>

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

---

**See the full implementation document for all code files, including Fragment, Adapter, Data Models, Colors, Strings, and more.**

**Status**: Step 1 Complete - YouTube Home Screen UI  
**Next**: Step 2 - YouTube Shorts UI with vertical swipe
