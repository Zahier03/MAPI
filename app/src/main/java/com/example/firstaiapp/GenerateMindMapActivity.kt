package com.example.firstaiapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

class GenerateMindMapActivity : AppCompatActivity() {
    private lateinit var mindMapView: MindMapView
    private var mindMapName = "change mind map name"
    private lateinit var editButton: Button
    private lateinit var addBranchButton: Button
    private lateinit var editExplanationButton: Button
    private lateinit var changeColorButton: Button
    private lateinit var undoButton: Button
    private lateinit var drawButton: Button
    private lateinit var cancelDrawButton: Button
    private lateinit var changePaintColorButton: Button
    private val PICK_IMAGE_REQUEST = 1
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var verticalScrollView: ScrollView
    private val undoStack = mutableListOf<UndoAction>()

    //dummy code to solve error for isScrollable
    var ScrollView.isScrollable: Boolean
        get() = true
        set(value) {
            // This is a dummy setter. ScrollView is always scrollable.
        }

    var HorizontalScrollView.isScrollable: Boolean
        get() = true
        set(value) {
            // This is a dummy setter. HorizontalScrollView is always scrollable.
        }

    private sealed class UndoAction {
        data class AddBox(val box: MindMapView.Box) : UndoAction()
        data class DeleteBox(val box: MindMapView.Box, val parent: MindMapView.Box?) : UndoAction()
        data class EditBox(val box: MindMapView.Box, val oldTitle: String, val oldExplanation: String) : UndoAction()
        data class AddSticker(val sticker: MindMapView.Sticker) : UndoAction()
        data class DeleteSticker(val sticker: MindMapView.Sticker) : UndoAction()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_mind_map)

        mindMapView = findViewById(R.id.mind_map_view)
        verticalScrollView = findViewById(R.id.vertical_scroll_view)
        horizontalScrollView = findViewById(R.id.horizontal_scroll_view)
        val exportButton = findViewById<Button>(R.id.export_button)
        val zoomInButton = findViewById<Button>(R.id.zoom_in_button)
        val zoomOutButton = findViewById<Button>(R.id.zoom_out_button)
        val deleteButton: Button = findViewById(R.id.delete_button)
        val resetViewButton: Button = findViewById(R.id.reset_view_button)
        editButton = findViewById(R.id.edit_button)
        addBranchButton = findViewById(R.id.add_branch_button)
        editExplanationButton = findViewById(R.id.edit_explanation_button)
        changeColorButton = findViewById(R.id.change_color_button)
        undoButton = findViewById(R.id.undo_button)
        drawButton = findViewById(R.id.draw_button)
        cancelDrawButton = findViewById(R.id.cancel_draw_button)
        changePaintColorButton = findViewById(R.id.change_paint_color_button)
        changePaintColorButton.visibility = View.GONE
        val changeThemeButton: Button = findViewById(R.id.change_theme_button)
        changeThemeButton.setOnClickListener {
            showThemeSelectionDialog()
        }


        changePaintColorButton.setOnClickListener {
            showDrawColorSelectionDialog()
        }

        cancelDrawButton.visibility = View.GONE

        horizontalScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> mindMapView.parent.requestDisallowInterceptTouchEvent(false)
                MotionEvent.ACTION_UP -> mindMapView.parent.requestDisallowInterceptTouchEvent(true)
            }
            false
        }

        verticalScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> mindMapView.parent.requestDisallowInterceptTouchEvent(false)
                MotionEvent.ACTION_UP -> mindMapView.parent.requestDisallowInterceptTouchEvent(true)
            }
            false
        }

        cancelDrawButton.setOnClickListener {
            exitDrawMode()
        }

        drawButton.setOnClickListener {
            toggleDrawMode()
        }


        undoButton.setOnClickListener {
            mindMapView.undo()
        }

        resetViewButton.setOnClickListener {
            resetView()
        }

        changeColorButton.setOnClickListener {
            showColorSelectionDialog()
        }

        editButton.setOnClickListener {
            if (mindMapView.selectedBox != null) {
                showEditDialog(mindMapView.selectedBox!!)
            } else {
                Toast.makeText(this, "Please select a branch to edit", Toast.LENGTH_SHORT).show()
            }
        }

        deleteButton.setOnClickListener {
            deleteSelectedItem()
        }

        addBranchButton.setOnClickListener {
            showAddBranchDialog()
        }

        editExplanationButton.setOnClickListener {
            if (mindMapView.selectedBox != null) {
                showEditExplanationDialog(mindMapView.selectedBox!!)
            } else {
                Toast.makeText(this, "Please select a branch to edit its explanation", Toast.LENGTH_SHORT).show()
            }
        }
        val addStickerButton: Button = findViewById(R.id.add_sticker_button)
        addStickerButton.setOnClickListener {
            openImageChooser()
        }

        val mindMapXml = intent.getStringExtra("mindMapXml")

        if (mindMapXml != null) {
            val mindMapData = parseXmlToMap(mindMapXml)
            mindMapView.setData(mindMapData)
            mindMapView.invalidate()
        }

        mindMapView.post {
            val (centerX, centerY) = mindMapView.getCenterCoordinates()

            val scrollX = (centerX - horizontalScrollView.width / 2).toInt()
            val scrollY = (centerY - verticalScrollView.height / 2).toInt()

            horizontalScrollView.scrollTo(scrollX, 0)
            verticalScrollView.scrollTo(0, scrollY)
        }

        zoomInButton.setOnClickListener {
            mindMapView.setZoomLevel(mindMapView.zoomLevel + 0.1f)
            mindMapView.invalidate()
            mindMapView.requestLayout()
        }

        zoomOutButton.setOnClickListener {
            mindMapView.setZoomLevel(mindMapView.zoomLevel - 0.1f)
            mindMapView.invalidate()
            mindMapView.requestLayout()
        }

        exportButton.setOnClickListener {
            showNameInputDialog()
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf("Light", "Dark", "Colorful")
        AlertDialog.Builder(this)
            .setTitle("Select Theme")
            .setItems(themes) { _, which ->
                val selectedTheme = when (which) {
                    0 -> MindMapView.Theme.LIGHT
                    1 -> MindMapView.Theme.DARK
                    2 -> MindMapView.Theme.COLORFUL
                    else -> MindMapView.Theme.LIGHT
                }
                mindMapView.changeTheme(selectedTheme)
            }
            .show()
    }

    private fun toggleDrawMode() {
        val isDrawing = !mindMapView.isDrawingMode
        mindMapView.isDrawingMode = isDrawing
        drawButton.isSelected = isDrawing
        updateButtonStates()
        updateScrollingAndCancelButton(isDrawing)
        changePaintColorButton.visibility = if (isDrawing) View.VISIBLE else View.GONE
    }

    private fun exitDrawMode() {
        mindMapView.isDrawingMode = false
        drawButton.isSelected = false
        updateButtonStates()
        updateScrollingAndCancelButton(false)
        changePaintColorButton.visibility = View.GONE
    }

    private fun showDrawColorSelectionDialog() {
        val colors = arrayOf("Black", "Red", "Blue", "Green", "Custom Color")
        AlertDialog.Builder(this)
            .setTitle("Select Drawing Color")
            .setItems(colors) { _, which ->
                when (which) {
                    0 -> mindMapView.setDrawColor(Color.BLACK)
                    1 -> mindMapView.setDrawColor(Color.RED)
                    2 -> mindMapView.setDrawColor(Color.BLUE)
                    3 -> mindMapView.setDrawColor(Color.GREEN)
                    4 -> showDrawColorWheel()
                }
            }
            .show()
    }

    private fun showDrawColorWheel() {
        ColorPickerDialog.Builder(this)
            .setTitle("Choose custom color")
            .setPreferenceName("MyDrawColorPickerDialog")
            .setPositiveButton("Select", ColorEnvelopeListener { envelope, _ ->
                val color = Color.parseColor("#" + envelope.hexCode)
                mindMapView.setDrawColor(color)
            })
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .attachAlphaSlideBar(true)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(12)
            .show()
    }

    private fun updateScrollingAndCancelButton(isDrawing: Boolean) {
        horizontalScrollView.isScrollable = !isDrawing
        verticalScrollView.isScrollable = !isDrawing
        cancelDrawButton.visibility = if (isDrawing) View.VISIBLE else View.GONE
    }

    private fun updateButtonStates() {
        val isDrawing = mindMapView.isDrawingMode
        editButton.isEnabled = !isDrawing
        addBranchButton.isEnabled = !isDrawing
        editExplanationButton.isEnabled = !isDrawing
        changeColorButton.isEnabled = !isDrawing
    }


    private fun showColorSelectionDialog() {
        if (mindMapView.selectedBox == null) {
            Toast.makeText(this, "Please select a branch to change its color", Toast.LENGTH_SHORT).show()
            return
        }

        val colors = arrayOf("Default", "Pastel Red", "Pastel Blue", "Pastel Yellow", "Custom Color")
        AlertDialog.Builder(this)
            .setTitle("Select Color")
            .setItems(colors) { _, which ->
                when (which) {
                    0 -> mindMapView.changeBoxColor("default")
                    1 -> mindMapView.changeBoxColor("pastel_red")
                    2 -> mindMapView.changeBoxColor("pastel_blue")
                    3 -> mindMapView.changeBoxColor("pastel_yellow")
                    4 -> showColorWheel()
                }
            }
            .show()
    }

    private fun showColorWheel() {
        ColorPickerDialog.Builder(this)
            .setTitle("Choose custom color")
            .setPreferenceName("MyColorPickerDialog")
            .setPositiveButton("Select", ColorEnvelopeListener { envelope, _ ->
                val hexColor = "#" + envelope.hexCode
                mindMapView.changeBoxColor(hexColor)
            })
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .attachAlphaSlideBar(true)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(12)
            .show()
    }

    private fun showEditDialog(box: MindMapView.Box) {
        val input = EditText(this)
        input.setText(box.title)

        AlertDialog.Builder(this)
            .setTitle("Edit Branch")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val oldTitle = box.title
                val oldExplanation = box.explanation
                box.title = input.text.toString()
                undoStack.add(UndoAction.EditBox(box, oldTitle, oldExplanation))
                mindMapView.invalidate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddBranchDialog() {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Add Branch")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newBox = MindMapView.Box(
                    x = mindMapView.width / 2f - 200f,
                    y = mindMapView.height / 2f - 100f,
                    title = input.text.toString()
                )
                if (mindMapView.selectedBox != null) {
                    mindMapView.selectedBox!!.subBranches.add(newBox)
                }
                mindMapView.boxes.add(newBox)
                undoStack.add(UndoAction.AddBox(newBox))
                mindMapView.invalidate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditExplanationDialog(box: MindMapView.Box) {
        val input = EditText(this)
        input.setText(box.explanation)

        AlertDialog.Builder(this)
            .setTitle("Edit Explanation")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val oldTitle = box.title
                val oldExplanation = box.explanation
                box.explanation = input.text.toString()
                undoStack.add(UndoAction.EditBox(box, oldTitle, oldExplanation))
                mindMapView.invalidate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNameInputDialog() {
        val input = EditText(this)
        input.setText(mindMapName)

        AlertDialog.Builder(this)
            .setTitle("Enter Mind Map Name")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                mindMapName = input.text.toString()
                showExportOptions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExportOptions() {
        val options = arrayOf("Share as JPEG", "Share as PNG", "Share as PDF")
        AlertDialog.Builder(this)
            .setTitle("Export Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareImage(Bitmap.CompressFormat.JPEG, "image/jpeg", ".jpg")
                    1 -> shareImage(Bitmap.CompressFormat.PNG, "image/png", ".png")
                    2 -> sharePdf()
                }
            }
            .show()
    }

    private fun shareImage(format: Bitmap.CompressFormat, mimeType: String, extension: String) {
        val bytes = mindMapView.exportImage(format)
        val file = File(cacheDir, "$mindMapName$extension")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = mimeType
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.putExtra(Intent.EXTRA_SUBJECT, mindMapName)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share Mind Map"))
    }

    private fun sharePdf() {
        val bytes = mindMapView.exportPdf()
        val file = File(cacheDir, "$mindMapName.pdf")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "application/pdf"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.putExtra(Intent.EXTRA_SUBJECT, mindMapName)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share Mind Map PDF"))
    }

    private fun parseXmlToMap(xmlData: String): List<Branch> {
        val branches = mutableListOf<Branch>()
        var currentBranch: Branch? = null
        var currentSubBranch: Branch? = null
        var currentSubSubBranch: Branch? = null
        var currentExplanation: String

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xmlData.reader())

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "branch" -> {
                                val branchName = parser.getAttributeValue(null, "name")
                                currentBranch = Branch(branchName)
                                branches.add(currentBranch)
                            }
                            "sub-branch" -> {
                                val subBranchName = parser.getAttributeValue(null, "name")
                                currentSubBranch = Branch(subBranchName)
                                currentBranch?.subBranches?.add(currentSubBranch)
                            }
                            "sub-sub-branch" -> {
                                val subSubBranchName = parser.getAttributeValue(null, "name") ?: parser.nextText()
                                currentSubSubBranch = Branch(subSubBranchName)
                                currentSubBranch?.subBranches?.add(currentSubSubBranch)
                            }
                            "explanation" -> {
                                currentExplanation = ""
                                var depth = 1
                                while (depth > 0) {
                                    eventType = parser.next()
                                    when (eventType) {
                                        XmlPullParser.START_TAG -> depth++
                                        XmlPullParser.END_TAG -> depth--
                                        XmlPullParser.TEXT -> currentExplanation += parser.text
                                    }
                                }
                                when {
                                    currentSubSubBranch != null -> currentSubSubBranch.explanation = currentExplanation
                                    currentSubBranch != null -> currentSubBranch.explanation = currentExplanation
                                    currentBranch != null -> currentBranch.explanation = currentExplanation
                                }
                                continue
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "sub-sub-branch" -> currentSubSubBranch = null
                            "sub-branch" -> currentSubBranch = null
                            "branch" -> currentBranch = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return branches
    }

    data class Branch(
        val name: String,
        var explanation: String = "",
        val subBranches: MutableList<Branch> = mutableListOf()
    )

    private fun openImageChooser() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val imageUri: Uri = data.data!!
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                val sticker = mindMapView.addSticker(scaledBitmap)
                undoStack.add(UndoAction.AddSticker(sticker))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteSelectedItem() {
        if (mindMapView.selectedBox != null) {
            val box = mindMapView.selectedBox!!
            val parent = mindMapView.boxes.find { it.subBranches.contains(box) }
            mindMapView.deleteSelectedBranch()
            undoStack.add(UndoAction.DeleteBox(box, parent))
            Toast.makeText(this, "Branch deleted", Toast.LENGTH_SHORT).show()
        } else if (mindMapView.selectedSticker != null) {
            val sticker = mindMapView.selectedSticker!!
            mindMapView.deleteSelectedSticker()
            undoStack.add(UndoAction.DeleteSticker(sticker))
            Toast.makeText(this, "Sticker deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please select a branch or sticker to delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetView() {
        mindMapView.post {
            val (centerX, centerY) = mindMapView.getCenterCoordinates()

            val scrollX = (centerX - horizontalScrollView.width / 2f).toInt()
            val scrollY = (centerY - verticalScrollView.height / 2f).toInt()

            horizontalScrollView.smoothScrollTo(scrollX, 0)
            verticalScrollView.smoothScrollTo(0, scrollY)

            // Reset zoom level to default (assuming 1.0f is the default)
            mindMapView.setZoomLevel(1.0f)
            mindMapView.invalidate()
            mindMapView.requestLayout()
        }
    }
}