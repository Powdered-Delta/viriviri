package com.viriviri.app.meta

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.viriviri.core.state.HandoffExperimentMode

/** Creates the standalone Spatial control panel; it intentionally owns no media output. */
internal fun createImmersiveControlPanel(
    context: Context,
    onEnterPanel: () -> Unit,
    onModeSelected: (HandoffExperimentMode) -> Unit,
    initialMode: HandoffExperimentMode,
): View {
    val density = context.resources.displayMetrics.density
    val padding = (24 * density).toInt()
    val buttonTopMargin = (12 * density).toInt()

    lateinit var modeButtons: List<Button>
    fun updateModeButtons(selected: HandoffExperimentMode) {
        modeButtons.forEachIndexed { index, button ->
            val mode = HandoffExperimentMode.entries[index]
            button.text = if (mode == selected) "[x] ${mode.label}" else "[ ] ${mode.label}"
        }
    }

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(padding, padding, padding, padding)
        background = ColorDrawable(Color.rgb(23, 32, 51))

        addView(
            TextView(context).apply {
                text = "Video controls"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            Button(context).apply {
                text = "Enter 2D Panel"
                isAllCaps = false
                setOnClickListener { onEnterPanel() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = buttonTopMargin
            },
        )
        modeButtons = HandoffExperimentMode.entries.map { mode ->
            Button(context).apply {
                text = mode.label
                isAllCaps = false
                setOnClickListener {
                    updateModeButtons(mode)
                    onModeSelected(mode)
                }
            }
        }
        modeButtons.forEach { button ->
            addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = buttonTopMargin / 2 },
            )
        }
        updateModeButtons(initialMode)
    }
}
