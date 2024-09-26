package com.example.firstaiapp

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import java.io.ByteArrayOutputStream
import kotlin.math.pow

class MindMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private val boxColors = mapOf(
        "default" to Color.WHITE,
        "pastel_red" to Color.rgb(255, 179, 186),
        "pastel_blue" to Color.rgb(173, 216, 230),
        "pastel_yellow" to Color.rgb(255, 255, 186)
    )

    private var currentTheme = Theme.LIGHT
    private val colorfulBoxColors = mutableMapOf<Box, Int>()

    enum class Theme {
        LIGHT, DARK, COLORFUL
    }

    data class Sticker(
        val bitmap: Bitmap,
        var position: RectF,
        var rotation: Float = 0f,
        var scale: Float = 1f
    ) {
        fun contains(x: Float, y: Float): Boolean {
            return position.contains(x, y)
        }

        fun isNearResizeHandle(x: Float, y: Float): Boolean {
            val handleRadius = 20f
            return ((x - position.right).pow(2) + (y - position.bottom).pow(2)) <= handleRadius.pow(2)
        }
    }

    private var isResizingSticker = false

    data class ThemeColors(
        val background: Int,
        val canvas: Int,
        val nodeFill: Int,
        val nodeBorder: Int,
        val text: Int,
        val connection: Int,
        val dot: Int  // New property for dot color
    )


    // Update existing properties to use theme colors
    private val borderPaint = Paint().apply {
        strokeWidth = 5f
        isAntiAlias = true
        pathEffect = CornerPathEffect(10f)
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    private val themes = mapOf(
        Theme.LIGHT to ThemeColors(
            background = Color.WHITE,
            canvas = Color.rgb(250, 250, 250),
            nodeFill = Color.WHITE,
            nodeBorder = Color.BLACK,
            text = Color.BLACK,
            connection = Color.BLACK,
            dot = Color.BLACK  // Black dots for light mode
        ),
        Theme.DARK to ThemeColors(
            background = Color.rgb(30, 30, 30),
            canvas = Color.rgb(50, 50, 50),
            nodeFill = Color.rgb(70, 70, 70),
            nodeBorder = Color.WHITE,
            text = Color.BLACK,
            connection = Color.WHITE,
            dot = Color.WHITE  // White dots for dark mode
        ),
        Theme.COLORFUL to ThemeColors(
            background = Color.rgb(230, 255, 255),
            canvas = Color.rgb(200, 255, 255),
            nodeFill = Color.rgb(255, 200, 200),
            nodeBorder = Color.rgb(0, 100, 100),
            text = Color.rgb(0, 0, 100),
            connection = Color.rgb(100, 0, 100),
            dot = Color.BLACK  // White dots for colorful mode
        )
    )


    private val shapeAdjustHandleRadius = 20f
    private var isAdjustingShape = false
    private var adjustingShapeHandle: ShapeHandle? = null

    enum class ShapeHandle {
        LEFT, RIGHT
    }

    enum class ResizeEdge {
        RIGHT, BOTTOM, BOTTOM_RIGHT, SHAPE_LEFT, SHAPE_RIGHT, SHAPE
    }


    private var drawColor: Int = Color.BLACK
    private var drawSize: Float = 5f

    private val drawPaint = Paint().apply {
        color = drawColor
        strokeWidth = drawSize
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }


    private val drawPath = Path()
    private val drawings = mutableListOf<Pair<Path, Paint>>()

    var isDrawingMode = false
        set(value) {
            field = value
            if (value) {
            }
        }


    private val undoStack = mutableListOf<UndoAction>()

    private sealed class UndoAction {
        data class ChangeColor(val box: Box, val oldColor: String) : UndoAction()
        data class DeleteBranch(val box: Box, val parent: Box?, val allDeletedBoxes: List<Box>) : UndoAction()
        data class MoveBranch(val box: Box, val oldX: Float, val oldY: Float) : UndoAction()
        data class ResizeBox(val box: Box, val oldWidth: Float, val oldHeight: Float) : UndoAction()
        data class AddDrawing(val drawing: Pair<Path, Paint>) : UndoAction()
        data class RemoveDrawing(val drawing: Pair<Path, Paint>) : UndoAction()
        data class AdjustShape(val box: Box, val oldCornerRadius: Float) : UndoAction()
    }

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }

    private var selectionClearHandler = Handler(Looper.getMainLooper())
    private var selectionClearRunnable: Runnable? = null

    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private val gridSpacing = 50f // Spacing between dots
    private val dotRadius = 2f // Radius of each dot

    val stickers = mutableListOf<Sticker>()
    var selectedSticker: Sticker? = null
        private set
    private var stickerOffsetX: Float = 0f
    private var stickerOffsetY: Float = 0f

    private val resizeHandleRadius = 20f
    private var isResizing = false
    private var resizingEdge: ResizeEdge? = null

    var selectedBox: Box? = null
        private set

    var zoomLevel: Float = 1.0f
        private set

    fun setZoomLevel(level: Float) {
        zoomLevel = level.coerceIn(0.1f, 5.0f)
        requestLayout()
        invalidate()
    }

    private fun drawOriginalScale(canvas: Canvas) {
        val originalZoom = zoomLevel
        zoomLevel = 1.0f
        draw(canvas)
        zoomLevel = originalZoom
    }

    override fun requestLayout() {
        super.requestLayout()
        parent?.requestLayout()
    }

    private val selectedBoxPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }

    val boxes = mutableListOf<Box>()

    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    data class Box(
        var x: Float,
        var y: Float,
        var width: Float = 400f,
        var height: Float = 200f,
        var title: String,
        var explanation: String = "",
        var subBranches: MutableList<Box> = mutableListOf(),
        var color: String = "default",
        var cornerRadius: Float = 10f
    )

    private fun drawWrappedText(canvas: Canvas, box: Box) {
        val padding = 10f
        val availableWidth = box.width - (padding * 2)
        val titleLines = wrapText(box.title, availableWidth)
        val lineHeight = textPaint.fontSpacing
        var yPos = box.y + padding + lineHeight

        titleLines.forEach { line ->
            canvas.drawText(line, box.x + padding, yPos, textPaint)
            yPos += lineHeight
        }

        if (box.explanation.isNotEmpty()) {
            yPos += lineHeight / 2 // Add some space before the explanation
            val explanationLines = wrapText("Explanation: ${box.explanation}", availableWidth)
            explanationLines.forEach { line ->
                canvas.drawText(line, box.x + padding, yPos, textPaint)
                yPos += lineHeight
            }
        }
    }


    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            selectedSticker?.let { sticker ->
                val scaleFactor = detector.scaleFactor
                val newScale = sticker.scale * scaleFactor
                sticker.scale = newScale.coerceIn(0.5f, 3.0f)
                invalidate()
                return true
            }
            return false
        }
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((5000 * zoomLevel).toInt(), widthMeasureSpec)
        val height = resolveSize((5000 * zoomLevel).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    fun setDrawColor(color: Int) {
        drawColor = color
        drawPaint.color = color
        invalidate()
    }


    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.size - 1)
            when (action) {
                is UndoAction.ChangeColor -> {
                    action.box.color = action.oldColor
                }
                is UndoAction.DeleteBranch -> {
                    boxes.addAll(action.allDeletedBoxes)
                    action.parent?.subBranches?.add(action.box)
                }
                is UndoAction.MoveBranch -> {
                    action.box.x = action.oldX
                    action.box.y = action.oldY
                }
                is UndoAction.ResizeBox -> {
                    action.box.width = action.oldWidth
                    action.box.height = action.oldHeight
                }
                is UndoAction.AddDrawing -> {
                    drawings.remove(action.drawing)
                }

                is UndoAction.RemoveDrawing -> {
                    drawings.add(action.drawing)
                }

                is UndoAction.AdjustShape -> {
                    action.box.cornerRadius = action.oldCornerRadius}
            }
            invalidate()
        }
    }

    fun setData(data: List<GenerateMindMapActivity.Branch>) {
        boxes.clear()
        colorfulBoxColors.clear()

        if (data.isEmpty()) return

        val mainBranch = data.first()
        val canvasWidth = 11000f
        val xOffset = canvasWidth / 2 - 200f
        var yOffset = 400f

        val mainBox = Box(
            x = xOffset,
            y = yOffset,
            title = mainBranch.name,
            explanation = mainBranch.explanation
        )
        adjustBoxSize(mainBox)
        boxes.add(mainBox)

        yOffset += mainBox.height + 100f

        val horizontalSpacing = 600f
        val startXOffset = xOffset - (mainBranch.subBranches.size - 1) * horizontalSpacing / 2

        mainBranch.subBranches.forEachIndexed { index, subBranch ->
            val subBox = Box(
                x = startXOffset + index * horizontalSpacing,
                y = yOffset,
                title = subBranch.name,
                explanation = subBranch.explanation
            )
            adjustBoxSize(subBox)
            mainBox.subBranches.add(subBox)
            boxes.add(subBox)

            var subYOffset = subBox.y + subBox.height + 50f

            subBranch.subBranches.forEach { subSubBranch ->
                val subSubBox = Box(
                    x = subBox.x,
                    y = subYOffset,
                    title = subSubBranch.name,
                    explanation = subSubBranch.explanation,
                    width = 300f,
                    height = 150f
                )
                adjustBoxSize(subSubBox)
                subYOffset += subSubBox.height + 25f
                subBox.subBranches.add(subSubBox)
                boxes.add(subSubBox)
            }
        }

        if (currentTheme == Theme.COLORFUL) {
            assignColorfulColors()
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(zoomLevel, zoomLevel)

        // Set background color
        canvas.drawColor(themes[currentTheme]!!.background)

        // Draw dot grid background
        drawDotGrid(canvas)

        // Draw all saved drawings
        for ((path, paint) in drawings) {
            canvas.drawPath(path, paint)
        }

        // Draw the current path if in drawing mode
        if (isDrawingMode) {
            canvas.drawPath(drawPath, drawPaint)
        }

        // Draw connections
        borderPaint.color = themes[currentTheme]!!.connection
        boxes.forEach { box ->
            box.subBranches.forEach { subBox ->
                canvas.drawLine(
                    box.x + box.width / 2,
                    box.y + box.height,
                    subBox.x + subBox.width / 2,
                    subBox.y,
                    borderPaint
                )
            }
        }

        // Draw stickers
        stickers.forEach { sticker ->
            canvas.save()
            canvas.translate(sticker.position.left, sticker.position.top)
            canvas.rotate(
                sticker.rotation,
                sticker.position.width() / 2,
                sticker.position.height() / 2
            )
            canvas.scale(
                sticker.scale,
                sticker.scale,
                sticker.position.width() / 2,
                sticker.position.height() / 2
            )
            canvas.drawBitmap(sticker.bitmap, 0f, 0f, null)

            // Draw resize handle for selected sticker
            if (sticker == selectedSticker) {
                val handlePaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(
                    sticker.position.width(),
                    sticker.position.height(),
                    resizeHandleRadius,
                    handlePaint
                )
            }
            canvas.restore()
        }

        // Draw boxes
        boxes.forEach { box ->
            val boxPaint = Paint().apply {
                color = when {
                    currentTheme == Theme.COLORFUL -> colorfulBoxColors[box] ?: getRandomPastelColor()
                    box.color in boxColors -> boxColors[box.color] ?: themes[currentTheme]!!.nodeFill
                    else -> Color.parseColor(box.color) // Use the custom color
                }
                style = Paint.Style.FILL
            }

            // Draw box background
            canvas.drawRoundRect(
                box.x,
                box.y,
                box.x + box.width,
                box.y + box.height,
                box.cornerRadius,
                box.cornerRadius,
                boxPaint
            )

            // Draw box border
            borderPaint.color = themes[currentTheme]!!.nodeBorder
            canvas.drawRoundRect(
                box.x,
                box.y,
                box.x + box.width,
                box.y + box.height,
                box.cornerRadius,
                box.cornerRadius,
                borderPaint
            )

            // Draw box text
            textPaint.color = themes[currentTheme]!!.text
            drawWrappedText(canvas, box)

            // Draw selection highlight if the box is selected
            if (box == selectedBox) {
                val selectionPaint = Paint().apply {
                    color = Color.argb(50, 0, 0, 255) // Semi-transparent blue
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(
                    box.x,
                    box.y,
                    box.x + box.width,
                    box.y + box.height,
                    box.cornerRadius,
                    box.cornerRadius,
                    selectionPaint
                )

                // Draw resize handles
                val handlePaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(
                    box.x + box.width,
                    box.y + box.height / 2,
                    resizeHandleRadius,
                    handlePaint
                )
                canvas.drawCircle(
                    box.x + box.width / 2,
                    box.y + box.height,
                    resizeHandleRadius,
                    handlePaint
                )
                canvas.drawCircle(
                    box.x + box.width,
                    box.y + box.height,
                    resizeHandleRadius,
                    handlePaint
                )

                // Draw shape adjustment handles
                canvas.drawCircle(
                    box.x,
                    box.y + box.height / 2,
                    shapeAdjustHandleRadius,
                    handlePaint
                )
                canvas.drawCircle(
                    box.x + box.width,
                    box.y + box.height / 2,
                    shapeAdjustHandleRadius,
                    handlePaint
                )
            }
        }

        // Draw all saved drawings again (to ensure they appear on top)
        for ((path, paint) in drawings) {
            canvas.drawPath(path, paint)
        }

        // Draw the current path if in drawing mode
        if (isDrawingMode) {
            canvas.drawPath(drawPath, drawPaint)
        }

        canvas.restore()
    }

    private fun drawDotGrid(canvas: Canvas) {
        val width = width / zoomLevel
        val height = height / zoomLevel

        val gridPaint = Paint().apply {
            color = themes[currentTheme]!!.dot  // Use theme-specific dot color
            style = Paint.Style.FILL
        }

        for (x in 0..width.toInt() step gridSpacing.toInt()) {
            for (y in 0..height.toInt() step gridSpacing.toInt()) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius, gridPaint)
            }
        }
    }

    // Function to generate random pastel color
    private fun getRandomPastelColor(): Int {
        val red = (Math.random() * 55 + 200).toInt()
        val green = (Math.random() * 55 + 200).toInt()
        val blue = (Math.random() * 55 + 200).toInt()
        return Color.rgb(red, green, blue)
    }


    private fun adjustBoxSize(box: Box) {
        val padding = 30f
        val maxWidth = 400f

        // Adjust the width and height of the box based on the wrapped text
        val titleLines = wrapText(box.title, maxWidth - (padding * 2))
        val lineHeight = textPaint.fontSpacing
        var requiredHeight = lineHeight * titleLines.size + (padding * 2) // Adding padding to top and bottom

        if (box.explanation.isNotEmpty()) {
            val explanationLines = wrapText("Explanation: ${box.explanation}", maxWidth - (padding * 2))
            requiredHeight += lineHeight * explanationLines.size + padding // Add padding between title and explanation
        }

        box.width = maxWidth
        box.height = requiredHeight
    }

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        words.forEach { word ->
            if (textPaint.measureText(currentLine.toString() + word) < maxWidth) {
                currentLine.append(word).append(" ")
            } else {
                lines.add(currentLine.toString().trim())
                currentLine = StringBuilder(word).append(" ")
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString().trim())
        }

        return lines
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        val adjustedX = (event.x - translationX) / zoomLevel
        val adjustedY = (event.y - translationY) / zoomLevel

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                cancelSelectionClear()
                initialTouchX = event.x
                initialTouchY = event.y

                // Check for sticker selection or resize
                for (index in stickers.indices.reversed()) {
                    val sticker = stickers[index]
                    if (sticker.contains(adjustedX, adjustedY)) {
                        if (sticker == selectedSticker && sticker.isNearResizeHandle(adjustedX, adjustedY)) {
                            isResizingSticker = true
                        } else {
                            selectedSticker = sticker
                            selectedBox = null
                            stickerOffsetX = adjustedX - sticker.position.left
                            stickerOffsetY = adjustedY - sticker.position.top
                        }
                        stickers.removeAt(index)
                        stickers.add(sticker)
                        invalidate()
                        parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }

                // Check for resize handle selection
                selectedBox?.let { box ->
                    when {
                        isNearPoint(adjustedX, adjustedY, box.x + box.width, box.y + box.height / 2) -> {
                            isResizing = true
                            resizingEdge = ResizeEdge.RIGHT
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        isNearPoint(adjustedX, adjustedY, box.x + box.width / 2, box.y + box.height) -> {
                            isResizing = true
                            resizingEdge = ResizeEdge.BOTTOM
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        isNearPoint(adjustedX, adjustedY, box.x + box.width, box.y + box.height) -> {
                            isResizing = true
                            resizingEdge = ResizeEdge.BOTTOM_RIGHT
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        isNearPoint(adjustedX, adjustedY, box.x, box.y + box.height / 2) -> {
                            isAdjustingShape = true
                            adjustingShapeHandle = ShapeHandle.LEFT
                            resizingEdge = ResizeEdge.SHAPE_LEFT
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        isNearPoint(adjustedX, adjustedY, box.x + box.width, box.y + box.height / 2) -> {
                            isAdjustingShape = true
                            adjustingShapeHandle = ShapeHandle.RIGHT
                            resizingEdge = ResizeEdge.SHAPE_RIGHT
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }

                        else -> {}
                    }
                }

                // Box selection code
                var boxSelected = false
                for (index in boxes.indices.reversed()) {
                    val box = boxes[index]
                    if (adjustedX in box.x..(box.x + box.width) && adjustedY in box.y..(box.y + box.height)) {
                        selectedBox = box
                        selectedSticker = null
                        offsetX = adjustedX - box.x
                        offsetY = adjustedY - box.y
                        initialTouchX = event.x
                        initialTouchY = event.y
                        boxes.removeAt(index)
                        boxes.add(box)
                        invalidate()
                        parent.requestDisallowInterceptTouchEvent(true)
                        boxSelected = true
                        break
                    }
                }

                if (!boxSelected) {
                    clearSelection()
                }

                if (isDrawingMode) {
                    lastX = adjustedX
                    lastY = adjustedY
                    drawPath.moveTo(adjustedX, adjustedY)
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizingSticker) {
                    handleStickerResizing(event)
                } else if (selectedSticker != null && !isResizingSticker) {
                    moveSelectedSticker(adjustedX, adjustedY)
                } else if (isResizing) {
                    handleResizing(event)
                } else if (isAdjustingShape) {
                    handleShapeAdjustment(event)
                } else if (selectedBox != null) {
                    moveSelectedBox(event)
                } else if (isDrawingMode) {
                    drawPath.quadTo(lastX, lastY, (lastX + adjustedX) / 2, (lastY + adjustedY) / 2)
                    lastX = adjustedX
                    lastY = adjustedY
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isResizingSticker = false
                isResizing = false
                isAdjustingShape = false
                adjustingShapeHandle = null
                resizingEdge = null
                parent.requestDisallowInterceptTouchEvent(true)
                performClick()
                scheduleSelectionClear()

                if (isDrawingMode) {
                    drawPath.lineTo(adjustedX, adjustedY)
                    val newPath = Path(drawPath)
                    val newPaint = Paint(drawPaint)
                    drawings.add(Pair(newPath, newPaint))
                    undoStack.add(UndoAction.AddDrawing(Pair(newPath, newPaint)))
                    drawPath.reset()
                    invalidate()
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return true
    }

    private fun handleResizing(event: MotionEvent) {
        selectedBox?.let { box ->
            val oldWidth = box.width
            val oldHeight = box.height
            val deltaX = (event.x - initialTouchX) / zoomLevel
            val deltaY = (event.y - initialTouchY) / zoomLevel
            when (resizingEdge) {
                ResizeEdge.RIGHT -> box.width = (box.width + deltaX).coerceAtLeast(100f)
                ResizeEdge.BOTTOM -> box.height = (box.height + deltaY).coerceAtLeast(50f)
                ResizeEdge.BOTTOM_RIGHT -> {
                    box.width = (box.width + deltaX).coerceAtLeast(100f)
                    box.height = (box.height + deltaY).coerceAtLeast(50f)
                }
                null -> {}
                ResizeEdge.SHAPE -> TODO()
                ResizeEdge.SHAPE_LEFT -> TODO()
                ResizeEdge.SHAPE_RIGHT -> TODO()
            }
            undoStack.add(UndoAction.ResizeBox(box, oldWidth, oldHeight))
            initialTouchX = event.x
            initialTouchY = event.y
            invalidate()
        }
    }

    private fun moveSelectedSticker(adjustedX: Float, adjustedY: Float) {
        selectedSticker?.let { sticker ->
            sticker.position.offsetTo(
                adjustedX - stickerOffsetX,
                adjustedY - stickerOffsetY
            )
            invalidate()
        }
    }

    private fun moveSelectedBox(event: MotionEvent) {
        selectedBox?.let { box ->
            val deltaX = (event.x - initialTouchX) / zoomLevel
            val deltaY = (event.y - initialTouchY) / zoomLevel
            moveBoxAndChildren(box, deltaX, deltaY)
            initialTouchX = event.x
            initialTouchY = event.y
            invalidate()
        }
    }

    private fun isNearPoint(x: Float, y: Float, pointX: Float, pointY: Float): Boolean {
        val distance = kotlin.math.sqrt((x - pointX).pow(2) + (y - pointY).pow(2))
        return distance <= resizeHandleRadius
    }

    private fun moveBoxAndChildren(box: Box, deltaX: Float, deltaY: Float) {
        val oldX = box.x
        val oldY = box.y
        box.x = (box.x + deltaX).coerceIn(0f, width / zoomLevel - box.width)
        box.y = (box.y + deltaY).coerceIn(0f, height / zoomLevel - box.height)

        undoStack.add(UndoAction.MoveBranch(box, oldX, oldY))

        // Move all child branches recursively
        moveChildrenRecursively(box, deltaX, deltaY)
    }

    private fun moveChildrenRecursively(parent: Box, deltaX: Float, deltaY: Float) {
        parent.subBranches.forEach { child ->
            child.x += deltaX
            child.y += deltaY
            moveChildrenRecursively(child, deltaX, deltaY)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun clearSelection() {
        selectedBox = null
        selectedSticker = null
        invalidate()
    }

    private fun scheduleSelectionClear() {
        cancelSelectionClear()
        selectionClearRunnable = Runnable {
            clearSelection()
        }
        selectionClearHandler.postDelayed(selectionClearRunnable!!, 10000)
    }

    private fun cancelSelectionClear() {
        selectionClearRunnable?.let { selectionClearHandler.removeCallbacks(it) }
    }

    fun getCenterCoordinates(): Pair<Float, Float> {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        boxes.forEach { box ->
            minX = minOf(minX, box.x)
            minY = minOf(minY, box.y)
            maxX = maxOf(maxX, box.x + box.width)
            maxY = maxOf(maxY, box.y + box.height)
        }

        return Pair((minX + maxX) / 2, (minY + maxY) / 2)
    }

    fun exportImage(format: Bitmap.CompressFormat): ByteArray {
        val bounds = getDrawnBounds()
        val bitmap = Bitmap.createBitmap(
            bounds.width().toInt(),
            bounds.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.translate(-bounds.left, -bounds.top)

        // Draw background
        canvas.drawRect(0f, 0f, bounds.width(), bounds.height(), Paint().apply { color = Color.WHITE })

        // Draw dot grid
        val originalZoom = zoomLevel
        zoomLevel = 1.0f
        drawDotGrid(canvas)
        zoomLevel = originalZoom

        // Draw mind map content
        drawOriginalScale(canvas)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, 100, outputStream)
        return outputStream.toByteArray()
    }

    fun exportPdf(): ByteArray {
        val bounds = getDrawnBounds()
        val bitmap = Bitmap.createBitmap(
            bounds.width().toInt(),
            bounds.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.translate(-bounds.left, -bounds.top)

        // Draw background
        canvas.drawRect(0f, 0f, bounds.width(), bounds.height(), Paint().apply { color = Color.WHITE })

        // Draw dot grid
        val originalZoom = zoomLevel
        zoomLevel = 1.0f
        drawDotGrid(canvas)
        zoomLevel = originalZoom

        // Draw mind map content
        drawOriginalScale(canvas)

        val outputStream = ByteArrayOutputStream()
        val pdfDocument = PdfDocument(PdfWriter(outputStream))
        val document = Document(pdfDocument)

        val pdfPage = pdfDocument.addNewPage(PageSize(bounds.width(), bounds.height()))
        val pdfCanvas = PdfCanvas(pdfPage)

        val bitmapData = ImageDataFactory.create(bitmap.toByteArray())
        val image = Image(bitmapData)
        image.setFixedPosition(0f, 0f)
        image.scaleToFit(bounds.width(), bounds.height())
        document.add(image)

        document.close()
        return outputStream.toByteArray()
    }

    private fun getDrawnBounds(): RectF {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        // Consider boxes
        boxes.forEach { box ->
            minX = minOf(minX, box.x)
            minY = minOf(minY, box.y)
            maxX = maxOf(maxX, box.x + box.width)
            maxY = maxOf(maxY, box.y + box.height)
        }

        // Consider stickers
        stickers.forEach { sticker ->
            minX = minOf(minX, sticker.position.left)
            minY = minOf(minY, sticker.position.top)
            maxX = maxOf(maxX, sticker.position.right)
            maxY = maxOf(maxY, sticker.position.bottom)
        }

        // Add some padding
        val padding = 50f
        return RectF(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }


    fun deleteSelectedBranch() {
        selectedBox?.let { box ->
            val parent = boxes.find { it.subBranches.contains(box) }
            val allDeletedBoxes = mutableListOf<Box>()
            removeBoxAndSubbranchesRecursively(box, allDeletedBoxes)
            parent?.subBranches?.remove(box)
            undoStack.add(UndoAction.DeleteBranch(box, parent, allDeletedBoxes))
            selectedBox = null
            invalidate()
        }
    }

    private fun removeBoxAndSubbranchesRecursively(box: Box, deletedBoxes: MutableList<Box>) {
        boxes.remove(box)
        deletedBoxes.add(box)
        box.subBranches.forEach { subBox ->
            removeBoxAndSubbranchesRecursively(subBox, deletedBoxes)
        }
    }

    fun addSticker(bitmap: Bitmap): Sticker {
        val centerX = width / (2 * zoomLevel)
        val centerY = height / (2 * zoomLevel)
        val stickerSize = 200f
        val sticker = Sticker(
            bitmap = bitmap,
            position = RectF(
                centerX - stickerSize / 2,
                centerY - stickerSize / 2,
                centerX + stickerSize / 2,
                centerY + stickerSize / 2
            )
        )
        stickers.add(sticker)
        invalidate()
        return sticker
    }


    fun deleteSelectedSticker() {
        selectedSticker?.let { sticker ->
            stickers.remove(sticker)
            selectedSticker = null
            invalidate()
        }
    }

    private fun handleShapeAdjustment(event: MotionEvent) {
        selectedBox?.let { box ->
            val oldCornerRadius = box.cornerRadius
            val deltaX = (event.x - initialTouchX) / zoomLevel

            // Adjust corner radius based on horizontal movement and selected handle
            val adjustmentFactor = when (adjustingShapeHandle) {
                ShapeHandle.LEFT -> -deltaX * 0.5f  // Moving left increases roundness
                ShapeHandle.RIGHT -> deltaX * 0.5f  // Moving right increases roundness
                null -> 0f
            }

            box.cornerRadius = (box.cornerRadius + adjustmentFactor).coerceIn(0f, minOf(box.width, box.height) / 2)

            undoStack.add(UndoAction.AdjustShape(box, oldCornerRadius))
            initialTouchX = event.x
            invalidate()
        }
    }

    private fun handleStickerResizing(event: MotionEvent) {
        selectedSticker?.let { sticker ->
            val deltaX = (event.x - initialTouchX) / zoomLevel
            val deltaY = (event.y - initialTouchY) / zoomLevel

            val newWidth = (sticker.position.width() + deltaX).coerceAtLeast(50f)
            val newHeight = (sticker.position.height() + deltaY).coerceAtLeast(50f)

            val scaleX = newWidth / sticker.bitmap.width
            val scaleY = newHeight / sticker.bitmap.height
            sticker.scale = minOf(scaleX, scaleY)

            sticker.position.right = sticker.position.left + newWidth
            sticker.position.bottom = sticker.position.top + newHeight

            initialTouchX = event.x
            initialTouchY = event.y
            invalidate()
        }
    }

    fun changeBoxColor(color: String) {
        selectedBox?.let { box ->
            val oldColor = box.color
            box.color = color
            if (currentTheme == Theme.COLORFUL) {
                if (color in boxColors) {
                    colorfulBoxColors[box] = boxColors[color] ?: themes[currentTheme]!!.nodeFill
                } else {
                    colorfulBoxColors[box] = Color.parseColor(color)
                }
            } else {
                colorfulBoxColors.remove(box)
            }
            undoStack.add(UndoAction.ChangeColor(box, oldColor))
            invalidate()
        }
    }

    private var colorfulColorsAssigned = false

    fun changeTheme(theme: Theme) {
        if (theme == Theme.COLORFUL && currentTheme != Theme.COLORFUL) {
            colorfulColorsAssigned = false
            assignColorfulColors()
        } else if (theme != Theme.COLORFUL) {
            colorfulBoxColors.clear()
            colorfulColorsAssigned = false
        }
        currentTheme = theme
        invalidate()
    }

    private fun assignColorfulColors() {
        if (!colorfulColorsAssigned) {
            boxes.forEach { box ->
                if (box.color !in boxColors) {
                    colorfulBoxColors[box] = getRandomPastelColor()
                }
            }
            colorfulColorsAssigned = true
        }
    }

}