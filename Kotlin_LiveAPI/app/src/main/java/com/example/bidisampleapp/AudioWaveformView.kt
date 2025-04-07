package com.example.bidisampleapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.random.Random

class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#4285F4") // Default blue color
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val barWidth = 10f  // Width of each bar
    private val barSpacing = 5f // Space between bars
    private val barCount = 6    // Number of bars to display
    private val maxBarHeight = 60f
    private val minBarHeight = 10f

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

            // Create new animator
            animators[i] = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600 + Random.nextInt(400).toLong() // Random duration for natural feel
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()

                // Update bar heights during animation
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    targetHeights[i] = minBarHeight + (maxBarHeight - minBarHeight) * Random.nextFloat()
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

            // Animate bars back to minimum height
            animators[i] = ValueAnimator.ofFloat(barHeights[i], minBarHeight).apply {
                duration = 300
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

        // Draw each bar
        for (i in 0 until barCount) {
            val rect = RectF(
                startX,
                (height - barHeights[i]) / 2, // Center vertically
                startX + barWidth,
                (height + barHeights[i]) / 2  // Center vertically
            )
            canvas.drawRoundRect(rect, 4f, 4f, paint)
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