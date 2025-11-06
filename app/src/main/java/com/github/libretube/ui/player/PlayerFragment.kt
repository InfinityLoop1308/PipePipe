diff --git a/app/src/main/java/com/github/libretube/ui/player/PlayerFragment.kt b/app/src/main/java/com/github/libretube/ui/player/PlayerFragment.kt
index 1111111..2222222 100644
--- a/app/src/main/java/com/github/libretube/ui/player/PlayerFragment.kt
+++ b/app/src/main/java/com/github/libretube/ui/player/PlayerFragment.kt
@@ -1,6 +1,9 @@
 package com.github.libretube.ui.player
 
+import com.github.libretube.ui.views.SpeedOverlayView
+import android.widget.FrameLayout
+
 class PlayerFragment : Fragment() {
-    // existing members
+    private var speedOverlayView: SpeedOverlayView? = null
+    private var boostSpeed: Float = 2f
 
     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
         super.onViewCreated(view, savedInstanceState)
@@ -20,6 +23,29 @@ class PlayerFragment : Fragment() {
         // existing setup
+
+        // attach overlay to PlayerView overlay container if present
+        val overlayContainer = binding.playerView.findViewById<FrameLayout>(R.id.exo_overlay)
+        if (overlayContainer != null && speedOverlayView == null) {
+            speedOverlayView = SpeedOverlayView(requireContext()).apply {
+                layoutParams = FrameLayout.LayoutParams(
+                    FrameLayout.LayoutParams.MATCH_PARENT,
+                    FrameLayout.LayoutParams.MATCH_PARENT
+                )
+            }
+            overlayContainer.addView(speedOverlayView)
+        }
     }
 
+    private fun onLongPressStart() {
+        player?.setPlaybackSpeed(boostSpeed)
+        speedOverlayView?.showSpeed(boostSpeed)
+    }
+
+    private fun onLongPressSpeedChanged(newSpeed: Float) {
+        boostSpeed = newSpeed
+        player?.setPlaybackSpeed(newSpeed)
+        speedOverlayView?.updateSpeed(newSpeed)
+    }
+
+    private fun onLongPressEnd() {
+        player?.setPlaybackSpeed(1f)
+        speedOverlayView?.hideOverlay()
+    }
 }
