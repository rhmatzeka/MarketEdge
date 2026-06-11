package id.rahmat.marketedge.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.view.setPadding
import id.rahmat.marketedge.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.text(
    value: String,
    sizeSp: Float = 14f,
    colorRes: Int = R.color.marketedge_text_primary,
    style: Int = Typeface.NORMAL
): TextView = TextView(this).apply {
    text = value
    setTextColor(getColor(colorRes))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    typeface = Typeface.create(Typeface.DEFAULT, style)
    includeFontPadding = true
}

fun Context.iconButton(label: String, symbol: String): TextView = text(symbol, 22f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
    contentDescription = label
    gravity = Gravity.CENTER
    background = rounded(R.color.marketedge_card, 18)
    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
}

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(14))
    background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
}

fun Context.rounded(colorRes: Int, radiusDp: Int, strokeColorRes: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        color = ColorStateList.valueOf(getColor(colorRes))
        cornerRadius = dp(radiusDp).toFloat()
        strokeColorRes?.let { setStroke(dp(1), getColor(it)) }
    }

fun Context.roundedRaw(color: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

fun Context.screenScroll(): LinearLayout {
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(88))
    }
    val scroll = ScrollView(this).apply {
        setBackgroundColor(getColor(R.color.marketedge_background))
        addView(container)
    }
    container.tag = scroll
    return container
}

fun Context.horizontalChips(labels: List<String>, selectedIndex: Int = 0): HorizontalScrollView {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(12))
    }
    labels.forEachIndexed { index, label ->
        row.addView(chip(label, index == selectedIndex))
    }
    return HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }
}

fun Context.chip(label: String, selected: Boolean = false, positive: Boolean? = null): TextView {
    val bg = when {
        selected -> R.color.marketedge_accent
        positive == true -> R.color.marketedge_card_soft
        positive == false -> R.color.marketedge_card_soft
        else -> R.color.marketedge_surface
    }
    val color = when {
        selected -> R.color.white
        positive == true -> R.color.marketedge_positive
        positive == false -> R.color.marketedge_negative
        else -> R.color.marketedge_text_secondary
    }
    return text(label, 13f, color, if (selected) Typeface.BOLD else Typeface.NORMAL).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(7), dp(12), dp(7))
        background = rounded(bg, 18, if (selected) null else R.color.marketedge_hairline)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(8)
        }
    }
}

fun LinearLayout.addGap(dpValue: Int) {
    addView(Space(context), LinearLayout.LayoutParams(1, context.dp(dpValue)))
}

fun Context.topBar(title: String, showLogo: Boolean = false, onSearch: (() -> Unit)? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(10))
        if (showLogo) {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.logoiconapp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Logo MarketEdge"
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(10)
            })
        }
        val titleView = text(title, if (showLogo) 24f else 22f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
            if (showLogo) setTextColor(getColor(R.color.marketedge_accent))
        }
        addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton("Cari", "⌕").apply { setOnClickListener { onSearch?.invoke() } })
    }

fun Context.sectionHeader(title: String, action: String? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(title, 18f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        action?.let { addView(text(it, 13f, R.color.marketedge_accent, Typeface.BOLD)) }
    }

fun Context.editText(hintText: String): EditText = EditText(this).apply {
    hint = hintText
    setHintTextColor(getColor(R.color.marketedge_text_muted))
    setTextColor(getColor(R.color.marketedge_text_primary))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setSingleLine(true)
    imeOptions = EditorInfo.IME_ACTION_SEND
    inputType = InputType.TYPE_CLASS_TEXT
    background = rounded(R.color.marketedge_card, 22, R.color.marketedge_hairline)
    setPadding(dp(14), 0, dp(14), 0)
}

fun Context.coloredBlock(color: Int, heightDp: Int = 142): FrameLayout = FrameLayout(this).apply {
    background = roundedRaw(color, 8)
    addView(text("MarketEdge", 13f, R.color.white, Typeface.BOLD).apply {
        alpha = 0.82f
        setPadding(dp(12))
    })
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
}
