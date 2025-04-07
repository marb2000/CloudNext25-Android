package com.example.bidisampleapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.random.Random
import androidx.core.graphics.toColorInt

class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = "#4285F4".toColorInt() // Default Google blue color
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Increased bar width and spacing for a more prominent look
    private val barWidth = 18f  // Wider bars
    private val barSpacing = 8f // More spacing between bars
    private val barCount = 12    // Fewer bars for a cleaner look
    private val maxBarHeight = 200f // Taller maximum height
    private val minBarHeight = 20f  // Taller minimum height

    private val barHeights = FloatArray(barCount) { minBarHeight }
    private val targetHeights = FloatArray(barCount) { minBarHeight }
    private val animators = Array(barCount) { ValueAnimator() }

    private var isAnimating = false

    init {
        // Initialize with minimum heights
        for (i in 0 until barCount) {
            barHeights[i] = minBarHeight
            targetHeights[i] = minBarHeight
        }
    }

    fun startAnimation() {
        isAnimating = true

        // Create and start animators for each bar
        for (i in 0 until barCount) {
            animators[i].cancel() // Cancel any running animation

            // Create new animator with slower animation
            animators[i] = ValueAnimator.ofFloat(0f, 1f).apply {
                // Longer duration for slower movement
                duration = 1500 + Random.nextInt(800).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                // Slower interpolation
                interpolator = LinearInterpolator()

                // Update bar heights during animation
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    // Smoother movement with less random jumps
                    targetHeights[i] = minBarHeight + (maxBarHeight - minBarHeight) *
                            (0.4f + 0.6f * Random.nextFloat())
                    barHeights[i] = minBarHeight + (targetHeights[i] - minBarHeight) * progress
                    invalidate()
                }
                start()
            }
        }
    }

    fun stopAnimation() {
        isAnimating = false
        for (i in 0 until barCount) {
            animators[i].cancel()

            // Animate bars back to minimum height with smooth transition
            animators[i] = ValueAnimator.ofFloat(barHeights[i], minBarHeight).apply {
                duration = 2000 // Longer duration for smoother transition
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    barHeights[i] = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate total width of all bars and spacing
        val totalWidth = barCount * barWidth + (barCount - 1) * barSpacing

        // Start position (center the bars)
        var startX = (width - totalWidth) / 2

        // Draw each bar with more rounded corners
        for (i in 0 until barCount) {
            val rect = RectF(
                startX,
                (height - barHeights[i]) / 2, // Center vertically
                startX + barWidth,
                (height + barHeights[i]) / 2  // Center vertically
            )
            // Use more rounded corners (8f instead of 4f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            startX += barWidth + barSpacing
        }
    }

    // Update the color of the waveform
    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up animators
        for (animator in animators) {
            animator.cancel()
        }
    }
}