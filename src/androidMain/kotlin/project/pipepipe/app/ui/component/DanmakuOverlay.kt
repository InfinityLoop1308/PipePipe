package project.pipepipe.app.ui.component

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.annotation.FontRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import project.pipepipe.shared.infoitem.DanmakuInfo
import java.util.Collections
import java.util.IdentityHashMap
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import androidx.compose.ui.platform.LocalContext


import project.pipepipe.app.MR

@Immutable
data class DanmakuConfig(
    val commentsDurationSeconds: Int = 8,
    val regularDurationFactor: Float = 1f,
    val outlineRadius: Float = 2f,
    val opacity: Int = 0xFF,
    val commentsRowsCount: Int = 11,
    val maxRowsTop: Int = Int.MAX_VALUE,
    val maxRowsBottom: Int = Int.MAX_VALUE,
    val maxRowsRegular: Int = Int.MAX_VALUE,
    val commentRelativeTextSize: Float = 1f / 13.5f,
    val typeface: Typeface = Typeface.DEFAULT
)

@Immutable
data class DanmakuStateConfig(
    val config: DanmakuConfig = DanmakuConfig()
)

@Composable
fun rememberDanmakuState(
    stateConfig: DanmakuStateConfig = DanmakuStateConfig(),
): DanmakuState {
    val density = LocalDensity.current
    val context = LocalContext.current
    val typeface = remember {
        // 使用 MOKO 的方式获取 Typeface (Android)
        MR.fonts.lxgw_wenkai.getTypeface(context)!!
    }
    val configWithFont = remember(stateConfig, typeface) {
        stateConfig.config.copy(typeface = typeface)
    }
    return remember(configWithFont, density) {
        DanmakuState(configWithFont)
    }
}

class DanmakuState internal constructor(
    private val config: DanmakuConfig
) {
    private val danmakuComparator =
        compareBy<DanmakuInfo> { it.timestamp?.inWholeMilliseconds ?: Long.MIN_VALUE }

    private var playbackSpeed: Float = 1f
    private var isPlaying: Boolean = true

    private val regularQueue = PriorityQueue(danmakuComparator)
    private val fixedQueue = PriorityQueue(danmakuComparator)

    private val consumed =
        Collections.newSetFromMap(IdentityHashMap<DanmakuInfo, Boolean>())
    private val rows = ArrayList<Long>()
    private val rowsRegular = ArrayList<RowRegularState>()
    private val active = mutableListOf<ActiveDanmaku>()

    private var widthPx: Float = 0f
    private var heightPx: Float = 0f
    private var rowCount: Int = 1
    private var internalPlaybackTimeMs: Long = Long.MIN_VALUE
    private var lastExternalPlaybackTimeMs: Long = Long.MIN_VALUE
    private var lastFrameTimeMs: Long = Long.MIN_VALUE

    private val seekThresholdMs = 500L

    fun updatePlaybackSpeed(speed: Float) {
        if (speed <= 0f || speed == playbackSpeed) return
        playbackSpeed = speed.coerceAtLeast(0.1f)
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    fun submitPool(pool: Iterable<DanmakuInfo>) {
        pool.forEach { info ->
            if (consumed.add(info)) {
                when (info.position) {
                    DanmakuInfo.Position.REGULAR -> regularQueue.add(info)
                    else -> fixedQueue.add(info)
                }
            }
        }
    }

    fun onSizeChanged(newWidth: Int, newHeight: Int) {
        val w = newWidth.toFloat()
        val h = newHeight.toFloat()
        if (w <= 0f || h <= 0f) return
        if (w == widthPx && h == heightPx) return
        widthPx = w
        heightPx = h
        updateRowCaches(forceResetOccupancy = true)
    }

    fun draw(
        drawScope: DrawScope,
        playbackTimeMs: Long,
        frameTimeMs: Long
    ) {
        if (widthPx <= 0f || heightPx <= 0f) return

        if (internalPlaybackTimeMs == Long.MIN_VALUE) {
            internalPlaybackTimeMs = playbackTimeMs
            lastExternalPlaybackTimeMs = playbackTimeMs
            lastFrameTimeMs = frameTimeMs
        } else {
            val externalDelta = playbackTimeMs - lastExternalPlaybackTimeMs
            if (abs(externalDelta) > seekThresholdMs) {
                onSeek()
                internalPlaybackTimeMs = playbackTimeMs
            } else if (lastFrameTimeMs != Long.MIN_VALUE && isPlaying) {
                val frameDelta = (frameTimeMs - lastFrameTimeMs).coerceAtLeast(0L)
                val advanced = (frameDelta.toDouble() * playbackSpeed.toDouble()).roundToLong()
                internalPlaybackTimeMs += advanced
            }
            lastExternalPlaybackTimeMs = playbackTimeMs
            lastFrameTimeMs = frameTimeMs
        }

        val effectiveTimeMs = internalPlaybackTimeMs

        spawnFromQueue(regularQueue, effectiveTimeMs, isRegular = true)
        spawnFromQueue(fixedQueue, effectiveTimeMs, isRegular = false)
        if (active.isEmpty()) return

        val iterator = active.iterator()
        drawScope.drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            while (iterator.hasNext()) {
                val danmaku = iterator.next()
                val elapsedPlayback =
                    (effectiveTimeMs - danmaku.startPlaybackTimeMs).coerceAtLeast(0L)
                val rawProgress = if (danmaku.durationMs <= 0L) {
                    1f
                } else {
                    (elapsedPlayback.toDouble() / danmaku.durationMs.toDouble()).toFloat()
                }
                val motionProgress = rawProgress.coerceIn(0f, 1f)

                val x = if (danmaku.isRegular) {
                    danmaku.startX + (danmaku.endX - danmaku.startX) * motionProgress
                } else {
                    danmaku.startX
                }

                val shouldRemove = if (danmaku.isRegular) {
                    rawProgress >= 1f || x + danmaku.textWidth <= 0f
                } else {
                    rawProgress >= 1f
                }

                if (!shouldRemove) {
                    nativeCanvas.drawText(danmaku.text, x, danmaku.baseline, danmaku.paint)
                }

                if (shouldRemove) iterator.remove()
            }
        }
    }

    fun clear() {
        internalClear()
    }

    fun onSeek() {
        internalClear()
    }

    private fun spawnFromQueue(
        queue: PriorityQueue<DanmakuInfo>,
        currentTimeMs: Long,
        isRegular: Boolean
    ) {
        while (queue.isNotEmpty()) {
            val info = queue.peek()

            if (info.text.isEmpty()) {
                queue.poll()
                continue
            }

            if (!info.isLive) {
                val scheduled = info.timestamp?.inWholeMilliseconds
                if (scheduled != null) {
                    val duration = computeDuration(info)
                    if (currentTimeMs >= scheduled + duration) {
                        queue.poll()
                        continue
                    }
                }
            }

            if (!shouldSpawnNow(info, currentTimeMs)) {
                return
            }

            queue.poll()
            val row =
                tryToPlaceDanmaku(info, rowCount, widthPx, currentTimeMs, reallyDo = true)
            if (row == -1) continue
            active.add(buildActiveDanmaku(info, row, currentTimeMs, isRegular))
        }
    }

    private fun shouldSpawnNow(
        info: DanmakuInfo,
        currentTimeMs: Long
    ): Boolean {
        if (info.isLive) return true
        val scheduled = info.timestamp?.inWholeMilliseconds ?: return true
        return scheduled <= currentTimeMs
    }

    private fun buildActiveDanmaku(
        info: DanmakuInfo,
        row: Int,
        currentTimeMs: Long,
        isRegular: Boolean
    ): ActiveDanmaku {
        val minDim = min(widthPx, heightPx)
        val actualTextSize =
            (minDim * config.commentRelativeTextSize * info.relativeFontSize).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = actualTextSize
            color = applyOpacity(info.argbColor, config.opacity)
            typeface = Typeface.create(config.typeface, Typeface.BOLD)
            if (config.outlineRadius > 0f) {
                val shadowColor = applyOpacity(0xFF000000.toInt(), config.opacity)
                setShadowLayer(config.outlineRadius, 0f, 0f, shadowColor)
            }
        }

        val text = info.text
        val textWidth = paint.measureText(text)
        val fontMetrics = paint.fontMetrics
        val centerY = heightPx * ((row + 0.5f) / rowCount.toFloat())
        val baseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f

        val baseDurationMs = computeDuration(info)
        val adjustedDurationMs = (baseDurationMs / playbackSpeed).toLong()
        val (startX, endX) = if (isRegular) {
            widthPx to -textWidth
        } else {
            val x = (widthPx - textWidth) / 2f
            x to x
        }

        return ActiveDanmaku(
            info = info,
            row = row,
            paint = paint,
            text = text,
            textWidth = textWidth,
            baseline = baseline,
            durationMs = adjustedDurationMs,
            startX = startX,
            endX = endX,
            isRegular = isRegular,
            startPlaybackTimeMs = currentTimeMs
        )
    }

    private fun computeDuration(info: DanmakuInfo): Long {
        val base = config.commentsDurationSeconds * 1000L
        val ratio = if (info.position == DanmakuInfo.Position.REGULAR) {
            config.regularDurationFactor
        } else {
            1f
        }
        val fallback = (base * ratio).roundToInt().toLong()
        return fallback.coerceAtLeast(1_000L)
    }

    private fun applyOpacity(color: Int, alpha: Int): Int {
        if (alpha == 0xFF) return color
        val mask = color and 0x00FFFFFF
        val actualAlpha = (alpha and 0xFF) shl 24
        return mask or actualAlpha
    }

    private fun tryToPlaceDanmaku(
        info: DanmakuInfo,
        rowsCount: Int,
        width: Float,
        currentTimeMs: Long,
        reallyDo: Boolean
    ): Int {
        val comparedDuration = config.commentsDurationSeconds * 1000
        val widthValue = width.toDouble()
        var row = -1

        when (info.position) {
            DanmakuInfo.Position.TOP -> {
                val limit = min(config.maxRowsTop, rowsCount)
                for (i in 0 until limit) {
                    val last = rows[i]
                    if (currentTimeMs - last >= comparedDuration) {
                        if (reallyDo) rows[i] = currentTimeMs
                        row = i
                        break
                    }
                }
            }

            DanmakuInfo.Position.REGULAR -> {
                val limit = min(config.maxRowsRegular, rowsCount)
                for (i in 0 until limit) {
                    val (lastTime, lastLength) = rowsRegular[i]
                    val t = currentTimeMs - lastTime
                    val tAll = comparedDuration * config.regularDurationFactor
                    val lx = (lastLength / 25.0 + 1) * widthValue
                    val ly = (info.text.length / 25.0 + 1) * widthValue
                    val vx = lx / tAll
                    val vy = ly / tAll
                    val collide =
                        (vy - vx) * (tAll - t) < t * vx - (lastLength / 25.0) * widthValue &&
                                t * vx - (lastLength / 25.0) * widthValue > 0

                    if (!collide) continue
                    if (reallyDo) {
                        rowsRegular[i] = RowRegularState(currentTimeMs, info.text.length)
                    }
                    row = i
                    break
                }
            }

            DanmakuInfo.Position.BOTTOM -> {
                val start = rowsCount - 1
                val end = max(0, rowsCount - config.maxRowsBottom)
                for (i in start downTo end) {
                    val last = rows[i]
                    if (currentTimeMs - last >= comparedDuration) {
                        if (reallyDo) rows[i] = currentTimeMs
                        row = i
                        break
                    }
                }
            }
        }

        return row
    }

    private fun updateRowCaches(forceResetOccupancy: Boolean) {
        val minDim = min(widthPx, heightPx)
        if (minDim <= 0f) return
        val computed = max(
            1,
            ((heightPx / minDim) * config.commentsRowsCount).roundToInt()
        )
        val rowCountChanged =
            computed != rowCount || rows.size != computed || rowsRegular.size != computed
        rowCount = computed
        if (rowCountChanged) {
            rows.clear()
            rowsRegular.clear()
            repeat(rowCount) {
                rows.add(0L)
                rowsRegular.add(RowRegularState(0L, 0))
            }
        } else if (forceResetOccupancy) {
            resetRowOccupancy()
        }
        active.clear()
    }

    private fun internalClear() {
        regularQueue.clear()
        fixedQueue.clear()
        consumed.clear()
        active.clear()
        resetRowOccupancy()
        resetInternalClock()
    }

    private fun resetRowOccupancy() {
        for (i in rows.indices) rows[i] = 0L
        for (i in rowsRegular.indices) rowsRegular[i] = RowRegularState(0L, 0)
    }

    private fun resetInternalClock() {
        internalPlaybackTimeMs = Long.MIN_VALUE
        lastExternalPlaybackTimeMs = Long.MIN_VALUE
        lastFrameTimeMs = Long.MIN_VALUE
    }

    private data class RowRegularState(
        val lastTimestampMs: Long,
        val lastLength: Int
    )

    private data class ActiveDanmaku(
        val info: DanmakuInfo,
        val row: Int,
        val paint: Paint,
        val text: String,
        val textWidth: Float,
        val baseline: Float,
        val durationMs: Long,
        val startX: Float,
        val endX: Float,
        val isRegular: Boolean,
        val startPlaybackTimeMs: Long
    )
}

@Composable
fun DanmakuOverlay(
    modifier: Modifier = Modifier,
    state: DanmakuState = rememberDanmakuState(),
    danmakuPool: List<DanmakuInfo>,
    playbackTimeMs: Long,
    enabled: Boolean = true
) {
    if (!enabled) return
    val frameTimeMs by rememberFrameClockMillis()
    Canvas(
        modifier = modifier.onSizeChanged { size ->
            state.onSizeChanged(size.width, size.height)
        }
    ) {
        state.submitPool(danmakuPool)
        state.draw(
            drawScope = this,
            playbackTimeMs = playbackTimeMs,
            frameTimeMs = frameTimeMs
        )
    }
}

@Composable
private fun rememberFrameClockMillis(): State<Long> =
    produceState(initialValue = SystemClock.elapsedRealtime()) {
        while (true) {
            value = withFrameNanos { it / 1_000_000L }
        }
    }