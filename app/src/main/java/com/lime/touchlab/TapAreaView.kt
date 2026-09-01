package com.lime.touchlab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Область выполнения TAP.
 *
 * Начиная с ACTION_DOWN обработчик удерживает весь поток до UP/CANCEL: возвращает
 * true из onTouchEvent и просит родителей не перехватывать события.
 *
 * requestDisallowInterceptTouchEvent защищает только от родительских View внутри
 * приложения. От системных краевых жестов — «назад» и шторки — он не спасает, для
 * них нужен systemGestureExclusionRects, поэтому здесь есть и то и другое. Система
 * при этом уважает исключение не более чем на 200 dp по вертикали на край, так что
 * сам по себе он безопасный отступ не заменяет — отступ считает Activity по инсетам.
 *
 * Про библиотеку View не знает: события уходят в [onEvent], а коллектор живёт дольше
 * экрана.
 */
class TapAreaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Куда уходит каждое событие. Вызывается на главном потоке. */
    var onEvent: ((MotionEvent) -> Unit)? = null

    /** Вызывается после терминального события, чтобы хост подготовил следующую попытку. */
    var onTrialTerminated: (() -> Unit)? = null

    /**
     * Палец на экране или нет.
     *
     * Нужно контроллеру: перевооружать попытку после возвращения из фона можно только
     * тогда, когда контакта нет, иначе перезапись состояния придётся на середину жеста.
     */
    var onContactChanged: ((Boolean) -> Unit)? = null

    /** Пока false, касания в коллектор не уходят: сессия не начата. */
    var acceptingTouches: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) setContact(false)
            invalidate()
        }

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#1F6152") }
    private val idlePaint = Paint().apply { color = Color.parseColor("#3C4643") }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private var lastX = -1f
    private var lastY = -1f
    private var inContact = false
    private val exclusionRects = mutableListOf<Rect>()

    init {
        isClickable = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        exclusionRects.clear()
        exclusionRects.add(Rect(0, 0, width, height))
        systemGestureExclusionRects = exclusionRects
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            if (acceptingTouches) backgroundPaint else idlePaint,
        )
        if (lastX >= 0) canvas.drawCircle(lastX, lastY, 24f, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!acceptingTouches) return false

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            setContact(true)
        }

        onEvent?.invoke(event)

        lastX = event.x
        lastY = event.y
        invalidate()

        val terminal = action == MotionEvent.ACTION_UP ||
            action == MotionEvent.ACTION_CANCEL ||
            action == MotionEvent.ACTION_POINTER_DOWN

        if (terminal) {
            parent?.requestDisallowInterceptTouchEvent(false)
            // Контакт снимается только на UP/CANCEL: после POINTER_DOWN пальцы ещё на
            // экране, и хвост прерванного жеста коллектор досчитывает отдельно.
            if (action != MotionEvent.ACTION_POINTER_DOWN) setContact(false)
            // Синхронно, на главном потоке: следующая попытка должна быть готова
            // раньше, чем палец успеет коснуться экрана снова.
            onTrialTerminated?.invoke()
        }
        return true
    }

    private fun setContact(value: Boolean) {
        if (inContact == value) return
        inContact = value
        onContactChanged?.invoke(value)
    }
}
