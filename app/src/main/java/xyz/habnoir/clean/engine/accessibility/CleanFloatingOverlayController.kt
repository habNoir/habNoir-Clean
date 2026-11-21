package xyz.habnoir.clean.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import xyz.habnoir.clean.R
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.model.CleanSessionState
import xyz.habnoir.clean.model.DeviceTier
import java.util.Locale
import kotlin.math.roundToInt

class CleanFloatingOverlayController(
    private val service: AccessibilityService,
    private val onTogglePause: () -> Unit,
    private val onStop: () -> Unit
) {
    private val overlayContext: Context = runCatching {
        val dm = service.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        } else {
            service
        }
    }.getOrElse { service }

    private val serviceWindowManager: WindowManager? = service.getSystemService(WindowManager::class.java)
    private var activeWindowManager: WindowManager? =
        overlayContext.getSystemService(WindowManager::class.java) ?: serviceWindowManager
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var statusDotView: View? = null
    private var batchTitleView: TextView? = null
    private var reclaimedView: TextView? = null
    private var detailTextView: TextView? = null
    private var progressTrack: FrameLayout? = null
    private var progressFill: View? = null
    private var pauseButton: TextView? = null
    private var stopButton: TextView? = null

    private var pulseAnimator: ObjectAnimator? = null
    private var progressAnimator: ValueAnimator? = null
    private var bytesAnimator: ValueAnimator? = null

    private var currentProgressFraction: Float = 0f
    private var displayedReclaimedBytes: Long = 0L
    private var targetReclaimedBytes: Long = 0L
    private var lastDetailText: String = ""

    fun render(state: CleanSessionState) {
        if (state !is CleanSessionState.Cleaning && state !is CleanSessionState.Cooldown) {
            dismiss()
            return
        }
        ensureAttached()
        val isPaused = when (state) {
            is CleanSessionState.Cleaning -> state.isPaused
            is CleanSessionState.Cooldown -> state.isPaused
            else -> false
        }
        val isWarm = when (state) {
            is CleanSessionState.Cleaning -> state.isPaused || state.policy.tier == DeviceTier.THERMAL_GUARD
            is CleanSessionState.Cooldown -> state.isPaused || state.isThermalThrottled
            else -> false
        }

        updateDotColor(isWarm)
        when (state) {
            is CleanSessionState.Cleaning -> bindCleaning(state)
            is CleanSessionState.Cooldown -> bindCooldown(state)
            else -> Unit
        }
        pauseButton?.text = service.getString(if (isPaused) R.string.btn_resume else R.string.btn_pause)
    }

    fun dismiss() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        progressAnimator?.cancel()
        progressAnimator = null
        bytesAnimator?.cancel()
        bytesAnimator = null

        val attached = rootView ?: return
        rootView = null
        layoutParams = null
        statusDotView = null
        batchTitleView = null
        reclaimedView = null
        detailTextView = null
        progressTrack = null
        progressFill = null
        pauseButton = null
        stopButton = null
        lastDetailText = ""
        currentProgressFraction = 0f
        displayedReclaimedBytes = 0L
        targetReclaimedBytes = 0L

        runCatching {
            activeWindowManager?.removeViewImmediate(attached)
            Log.i(TAG, "Dismissed accessibility overlay")
        }.onFailure { t ->
            Log.w(TAG, "Failed removing accessibility overlay", t)
        }
    }

    private fun bindCleaning(state: CleanSessionState.Cleaning) {
        batchTitleView?.text = service.getString(
            R.string.overlay_batch_format,
            state.currentBatch,
            state.totalBatches,
            state.processedApps,
            state.totalApps
        )
        val detail = if (state.isPaused) {
            service.getString(R.string.overlay_paused)
        } else {
            service.getString(R.string.overlay_cleaning_app, state.activeAppLabel, state.activeAppSize)
        }
        setDetailSmooth(detail, accent = state.isPaused)
        val targetFraction = if (state.totalApps > 0) {
            (state.processedApps.toFloat() / state.totalApps.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        animateProgress(targetFraction, accentColor = if (state.isPaused) COLOR_CHAMPAGNE else COLOR_ALABASTER)
        animateReclaimedBytes(state.reclaimedBytesSoFar)
    }

    private fun bindCooldown(state: CleanSessionState.Cooldown) {
        batchTitleView?.text = service.getString(
            R.string.overlay_batch_format,
            state.currentBatch,
            state.totalBatches,
            state.processedApps,
            state.totalApps
        )
        val detail = if (state.isPaused) {
            service.getString(R.string.overlay_paused)
        } else {
            val secText = String.format(Locale.US, "%.1f", state.remainingMs / 1000.0)
            service.getString(R.string.overlay_cooldown, secText)
        }
        setDetailSmooth(detail, accent = true)
        val targetFraction = if (state.totalApps > 0) {
            (state.processedApps.toFloat() / state.totalApps.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        animateProgress(targetFraction, accentColor = COLOR_CHAMPAGNE)
        animateReclaimedBytes(state.reclaimedBytesSoFar)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureAttached() {
        if (rootView != null) return
        val primaryWm = overlayContext.getSystemService(WindowManager::class.java) ?: serviceWindowManager ?: return

        val dm = overlayContext.resources.displayMetrics
        val maxPanelWidth = minOf(dm.widthPixels - dp(24), dp(384))
        val statusBarOffset = readStatusBarHeightPx(primaryWm) + dp(10)

        val container = LinearLayout(overlayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(COLOR_SURFACE)
                setStroke(dp(1), COLOR_HAIRLINE)
            }
            elevation = dp(8).toFloat()
        }

        // Top row: pulsing dot + batch info + reclaimed bytes
        val topRow = LinearLayout(overlayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(overlayContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_ALABASTER)
            }
        }
        statusDotView = dot
        topRow.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
            marginEnd = dp(8)
        })

        val batchLabel = TextView(overlayContext).apply {
            setTextColor(COLOR_SLATE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        batchTitleView = batchLabel
        topRow.addView(batchLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val reclaimedLabel = TextView(overlayContext).apply {
            setTextColor(COLOR_ALABASTER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            maxLines = 1
            text = service.getString(R.string.overlay_reclaimed_format, ByteFormat.format(0L))
        }
        reclaimedView = reclaimedLabel
        topRow.addView(reclaimedLabel)

        container.addView(topRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Middle status row
        val detailLabel = TextView(overlayContext).apply {
            setTextColor(COLOR_ALABASTER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        detailTextView = detailLabel
        container.addView(detailLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
        })

        // Animated progress bar
        val track = FrameLayout(overlayContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(COLOR_TRACK)
            }
        }
        val fill = View(overlayContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(COLOR_ALABASTER)
            }
        }
        track.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        track.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0) {
                applyProgressWidth(width, currentProgressFraction)
            }
        }
        progressTrack = track
        progressFill = fill
        container.addView(track, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(4)
        ).apply {
            topMargin = dp(10)
            bottomMargin = dp(12)
        })

        // Controls row
        val controlsRow = LinearLayout(overlayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pauseBtn = buildPillButton(
            text = service.getString(R.string.btn_pause),
            textColor = COLOR_ALABASTER,
            fillColor = COLOR_BUTTON_FILL,
            strokeColor = COLOR_ACTIVE_HAIRLINE,
            onClick = onTogglePause
        )
        pauseButton = pauseBtn
        controlsRow.addView(pauseBtn, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            marginEnd = dp(8)
        })

        val stopBtn = buildPillButton(
            text = service.getString(R.string.btn_stop),
            textColor = COLOR_ROSE,
            fillColor = COLOR_ROSE_FILL,
            strokeColor = COLOR_ROSE_BORDER,
            onClick = onStop
        )
        stopButton = stopBtn
        controlsRow.addView(stopBtn, LinearLayout.LayoutParams(0, dp(40), 1f))

        container.addView(controlsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val params = WindowManager.LayoutParams(
            maxPanelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarOffset
        }

        attachVerticalDrag(container, params, statusBarOffset, dm.heightPixels - dp(220))

        container.alpha = 0f
        container.translationY = -dp(14).toFloat()

        var attachedWm: WindowManager? = null
        runCatching {
            primaryWm.addView(container, params)
            attachedWm = primaryWm
            Log.i(TAG, "Attached accessibility overlay via primary window context")
        }.onFailure { primaryError ->
            Log.w(TAG, "Primary overlay attach failed, trying service WindowManager", primaryError)
            val fallbackWm = serviceWindowManager
            if (fallbackWm != null && fallbackWm !== primaryWm) {
                runCatching {
                    fallbackWm.addView(container, params)
                    attachedWm = fallbackWm
                    Log.i(TAG, "Attached accessibility overlay via fallback service WindowManager")
                }.onFailure { fallbackError ->
                    Log.e(TAG, "Failed to attach accessibility overlay", fallbackError)
                }
            } else {
                Log.e(TAG, "Failed to attach accessibility overlay", primaryError)
            }
        }

        if (attachedWm != null) {
            activeWindowManager = attachedWm
            rootView = container
            layoutParams = params
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            startDotPulse(dot)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPillButton(
        text: String,
        textColor: Int,
        fillColor: Int,
        strokeColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(overlayContext).apply {
            this.text = text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(fillColor)
                setStroke(dp(1), strokeColor)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80L).start()
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                }
                false
            }
            setOnClickListener { onClick() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachVerticalDrag(
        container: LinearLayout,
        params: WindowManager.LayoutParams,
        minY: Int,
        maxY: Int
    ) {
        var initialY = params.y
        var initialTouchY = 0f
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = (event.rawY - initialTouchY).roundToInt()
                    params.y = (initialY + delta).coerceIn(minY, maxOf(minY, maxY))
                    runCatching { activeWindowManager?.updateViewLayout(container, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun startDotPulse(dot: View) {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f).apply {
            duration = 850L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun updateDotColor(warmAccent: Boolean) {
        val drawable = statusDotView?.background as? GradientDrawable ?: return
        drawable.setColor(if (warmAccent) COLOR_CHAMPAGNE else COLOR_ALABASTER)
    }

    private fun setDetailSmooth(text: String, accent: Boolean) {
        val label = detailTextView ?: return
        label.setTextColor(if (accent) COLOR_CHAMPAGNE else COLOR_ALABASTER)
        if (lastDetailText != text) {
            lastDetailText = text
            label.alpha = 0.45f
            label.text = text
            label.animate().alpha(1f).setDuration(140L).start()
        }
    }

    private fun animateProgress(targetFraction: Float, accentColor: Int) {
        val clamped = targetFraction.coerceIn(0f, 1f)
        val fillDrawable = progressFill?.background as? GradientDrawable
        fillDrawable?.setColor(accentColor)

        progressAnimator?.cancel()
        val startFraction = currentProgressFraction
        progressAnimator = ValueAnimator.ofFloat(startFraction, clamped).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                currentProgressFraction = anim.animatedValue as Float
                val trackW = progressTrack?.width ?: 0
                if (trackW > 0) {
                    applyProgressWidth(trackW, currentProgressFraction)
                }
            }
            start()
        }
    }

    private fun applyProgressWidth(trackWidth: Int, fraction: Float) {
        val fill = progressFill ?: return
        val targetWidth = (trackWidth * fraction.coerceIn(0f, 1f)).roundToInt()
        val lp = fill.layoutParams
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            fill.layoutParams = lp
        }
    }

    private fun animateReclaimedBytes(target: Long) {
        if (target == targetReclaimedBytes) return
        targetReclaimedBytes = target
        val start = displayedReclaimedBytes
        bytesAnimator?.cancel()
        bytesAnimator = ValueAnimator.ofFloat(start.toFloat(), target.toFloat()).apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val value = (anim.animatedValue as Float).toLong()
                displayedReclaimedBytes = value
                reclaimedView?.text = service.getString(
                    R.string.overlay_reclaimed_format,
                    ByteFormat.format(value)
                )
            }
            start()
        }
    }

    private fun readStatusBarHeightPx(wm: WindowManager): Int {
        val topInset = runCatching {
            wm.currentWindowMetrics
                .windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top
        }.getOrNull()
        return if (topInset != null && topInset > 0) topInset else dp(32)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            overlayContext.resources.displayMetrics
        ).roundToInt()
    }

    companion object {
        private const val TAG = "HabNoirOverlay"
        private const val COLOR_SURFACE = 0xF011131B.toInt()
        private const val COLOR_TRACK = 0xFF171A24.toInt()
        private const val COLOR_BUTTON_FILL = 0xFF1C202E.toInt()
        private const val COLOR_HAIRLINE = 0xFF2B2E3C.toInt()
        private const val COLOR_ACTIVE_HAIRLINE = 0xFF3E4357.toInt()
        private const val COLOR_ALABASTER = 0xFFF4F2EC.toInt()
        private const val COLOR_SLATE = 0xFF8E93A6.toInt()
        private const val COLOR_CHAMPAGNE = 0xFFE2C799.toInt()
        private const val COLOR_ROSE = 0xFFE08A8A.toInt()
        private const val COLOR_ROSE_FILL = 0x1FE08A8A
        private const val COLOR_ROSE_BORDER = 0x73E08A8A
    }
}
