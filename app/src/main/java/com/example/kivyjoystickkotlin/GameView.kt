package com.example.kivyjoystickkotlin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

class GameView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val background: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.gameplay)

    private val playerBitmap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.player)

    private val outerJoystickBitmap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.outer_joystick)

    private val innerJoystickBitmap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.inner_joystick)

    private var playerSize = 0f
    private var outerSize = 0f
    private var innerSize = 0f
    private var joystickX = 0f
    private var joystickY = 0f
    private var playerX = 0f
    private var playerY = 0f
    private var innerX = 0f
    private var innerY = 0f
    private var dx = 0f
    private var dy = 0f

    private var joystickTouchId = MotionEvent.INVALID_POINTER_ID
    private val speed = 300f
    private var lastFrameTime = System.nanoTime()

    private val gameLoop = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = ((now - lastFrameTime) / 1_000_000_000.0).toFloat()
            lastFrameTime = now
            update(dt.coerceAtMost(0.05f))
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        isFocusable = true
        postOnAnimation(gameLoop)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        playerSize = w * 0.12f
        outerSize = w * 0.30f
        innerSize = w * 0.20f
        joystickX = w * 0.06f
        joystickY = h * 0.08f
        playerX = w / 2f - playerSize / 2f
        playerY = h / 2f - playerSize / 2f
        resetInner()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawPlayer(canvas)
        drawJoystick(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val srcRatio = background.width.toFloat() / background.height
        val dstRatio = width.toFloat() / height
        val dst: android.graphics.RectF
        if (srcRatio > dstRatio) {
            val scaledHeight = width / srcRatio
            dst = android.graphics.RectF(0f, (height - scaledHeight) / 2f, width.toFloat(), (height + scaledHeight) / 2f)
        } else {
            val scaledWidth = height * srcRatio
            dst = android.graphics.RectF((width - scaledWidth) / 2f, 0f, (width + scaledWidth) / 2f, height.toFloat())
        }
        canvas.drawBitmap(background, null, dst, paint)
    }

    private fun drawPlayer(canvas: Canvas) {
        val scaled = Bitmap.createScaledBitmap(
            playerBitmap,
            playerSize.toInt().coerceAtLeast(1),
            playerSize.toInt().coerceAtLeast(1),
            true
        )
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() - 90f
        val matrix = Matrix()
        matrix.postTranslate(-scaled.width / 2f, -scaled.height / 2f)
        matrix.postRotate(angle)
        matrix.postTranslate(playerX + playerSize / 2f, playerY + playerSize / 2f)
        canvas.drawBitmap(scaled, matrix, paint)
    }

    private fun drawJoystick(canvas: Canvas) {
        val outerDst = android.graphics.RectF(joystickX, joystickY, joystickX + outerSize, joystickY + outerSize)
        canvas.drawBitmap(outerJoystickBitmap, null, outerDst, paint)
        val innerDst = android.graphics.RectF(innerX, innerY, innerX + innerSize, innerY + innerSize)
        canvas.drawBitmap(innerJoystickBitmap, null, innerDst, paint)
    }

    private fun centerX(): Float = joystickX + outerSize / 2f
    private fun centerY(): Float = joystickY + outerSize / 2f

    private fun resetInner() {
        val cx = centerX()
        val cy = centerY()
        innerX = cx - innerSize / 2f
        innerY = cy - innerSize / 2f
        dx = 0f
        dy = 0f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (isInsideOuterJoystick(x, y)) {
                    joystickTouchId = event.getPointerId(0)
                    updateStick(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val x = event.getX(index)
                val y = event.getY(index)
                if (joystickTouchId == MotionEvent.INVALID_POINTER_ID && isInsideOuterJoystick(x, y)) {
                    joystickTouchId = event.getPointerId(index)
                    updateStick(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (joystickTouchId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(joystickTouchId)
                    if (index >= 0) {
                        updateStick(event.getX(index), event.getY(index))
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (joystickTouchId != MotionEvent.INVALID_POINTER_ID) {
                    joystickTouchId = MotionEvent.INVALID_POINTER_ID
                    resetInner()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                if (pointerId == joystickTouchId) {
                    joystickTouchId = MotionEvent.INVALID_POINTER_ID
                    resetInner()
                    return true
                }
            }
        }
        return true
    }

    private fun isInsideOuterJoystick(x: Float, y: Float): Boolean {
        return x >= joystickX && x <= joystickX + outerSize && y >= joystickY && y <= joystickY + outerSize
    }

    private fun updateStick(x: Float, y: Float) {
        val cx = centerX()
        val cy = centerY()
        var stickDx = x - cx
        var stickDy = y - cy
        val radius = outerSize / 2f - innerSize / 2f
        val distance = hypot(stickDx.toDouble(), stickDy.toDouble()).toFloat()
        if (distance > 0f) {
            dx = stickDx / distance
            dy = stickDy / distance
        }
        if (distance > radius) {
            stickDx = stickDx / distance * radius
            stickDy = stickDy / distance * radius
        }
        innerX = cx + stickDx - innerSize / 2f
        innerY = cy + stickDy - innerSize / 2f
    }

    private fun update(dt: Float) {
        if (joystickTouchId != MotionEvent.INVALID_POINTER_ID) {
            val ix = (innerX + innerSize / 2f) - centerX()
            val iy = (innerY + innerSize / 2f) - centerY()
            val deadZone = (outerSize / 2f - innerSize / 2f) * 0.35f
            if (hypot(ix.toDouble(), iy.toDouble()) > deadZone) {
                playerX += dx * speed * dt
                playerY += dy * speed * dt
            }
            playerX = playerX.coerceIn(0f, max(0f, width - playerSize))
            playerY = playerY.coerceIn(0f, max(0f, height - playerSize))
        }
    }
}
