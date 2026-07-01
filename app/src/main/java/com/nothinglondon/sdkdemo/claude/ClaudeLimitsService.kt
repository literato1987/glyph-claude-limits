package com.nothinglondon.sdkdemo.claude

import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothinglondon.sdkdemo.R
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import com.nothinglondon.sdkdemo.tesla.MatrixCircle
import com.nothinglondon.sdkdemo.tesla.MatrixPixelFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClaudeLimitsService : GlyphMatrixService("Claude-Limits") {

    private enum class ViewMode { PROGRESS, RESET }

    private lateinit var bgScope: CoroutineScope
    private var fetchJob: Job? = null
    private var latestLimits: ClaudeLimits = ClaudeLimits.EMPTY
    private var viewMode: ViewMode = ViewMode.PROGRESS
    private var lastFetchAtMs: Long = 0L

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager,
    ) {
        bgScope = CoroutineScope(Dispatchers.Default)
        viewMode = ViewMode.PROGRESS
        showLoading(glyphMatrixManager)
        requestRefresh(glyphMatrixManager, force = true)
    }

    override fun performOnServiceDisconnected(context: Context) {
        fetchJob?.cancel()
        fetchJob = null
        bgScope.cancel()
    }

    override fun onTouchPointPressed() {
        viewMode = ViewMode.PROGRESS
        glyphMatrixManager?.let { manager ->
            requestRefresh(manager, force = false)
        }
    }

    override fun onTouchPointLongPress() {
        viewMode = ViewMode.RESET
        glyphMatrixManager?.let { manager ->
            requestRefresh(manager, force = true)
        }
    }

    override fun onAodTick() {
        glyphMatrixManager?.let { manager ->
            requestRefresh(manager, force = false)
        }
    }

    private fun requestRefresh(glyphMatrixManager: GlyphMatrixManager, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAtMs < MIN_FETCH_INTERVAL_MS) {
            renderCurrentView(glyphMatrixManager)
            return
        }
        fetchJob?.cancel()
        fetchJob = bgScope.launch {
            refreshLimits(glyphMatrixManager)
            lastFetchAtMs = System.currentTimeMillis()
        }
    }

    private suspend fun refreshLimits(glyphMatrixManager: GlyphMatrixManager) {
        val limits = ClaudeLimitsProvider.refresh(applicationContext)
        latestLimits = limits
        withContext(Dispatchers.Main) {
            renderCurrentView(glyphMatrixManager)
        }
    }

    private fun showLoading(glyphMatrixManager: GlyphMatrixManager) {
        renderResetLine(glyphMatrixManager, "---")
    }

    private fun renderCurrentView(glyphMatrixManager: GlyphMatrixManager) {
        when (viewMode) {
            ViewMode.PROGRESS -> renderProgress(glyphMatrixManager)
            ViewMode.RESET -> renderReset(glyphMatrixManager)
        }
    }

    private fun renderProgress(glyphMatrixManager: GlyphMatrixManager) {
        val limits = latestLimits
        val used = limits.usedPercentage.coerceAtLeast(0)

        val bitmap = MatrixProgressRing.progressBitmap(
            applicationContext,
            R.drawable.claude_icon,
            used,
        )

        val frameObject = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setScale(100)
            .setPosition(0, 0)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addMid(frameObject)
            .build(applicationContext)

        glyphMatrixManager.setMatrixFrame(frame.render())
    }

    private fun renderReset(glyphMatrixManager: GlyphMatrixManager) {
        val limits = latestLimits
        if (limits.resetsAtEpochSec <= 0L) {
            renderResetLine(glyphMatrixManager, "--")
            return
        }
        val line = formatResetCountdown(limits.resetsAtEpochSec)
        renderResetLine(glyphMatrixManager, line)
    }

    private fun renderResetLine(glyphMatrixManager: GlyphMatrixManager, line: String) {
        val objectBuilder = if (needsPixelFont(line)) {
            GlyphMatrixObject.Builder()
                .setImageSource(
                    MatrixPixelFont.toDrawableBitmap(
                        applicationContext,
                        line,
                        MatrixCircle.LAYER_MID_Y,
                    ),
                )
                .setScale(100)
                .setPosition(0, 0)
        } else {
            GlyphMatrixObject.Builder()
                .setText(line)
                .setPosition(MatrixCircle.centerX(line.length), MatrixCircle.LAYER_MID_Y)
        }

        val frame = GlyphMatrixFrame.Builder()
            .addMid(objectBuilder.build())
            .build(applicationContext)

        glyphMatrixManager.setMatrixFrame(frame.render())
    }

    private fun needsPixelFont(text: String): Boolean {
        return text.any { it in ".+%:hm" || it.isLowerCase() }
    }

    private companion object {
        private const val TAG = "ClaudeLimitsService"
        private const val MIN_FETCH_INTERVAL_MS = 5 * 60_000L
    }
}