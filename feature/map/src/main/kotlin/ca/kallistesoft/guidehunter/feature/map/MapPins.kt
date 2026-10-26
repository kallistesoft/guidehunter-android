package ca.kallistesoft.guidehunter.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ca.kallistesoft.guidehunter.core.designsystem.theme.PinColors
import ca.kallistesoft.guidehunter.core.model.GuideActivity

/** The pin's colour for a guide's [activity]. */
internal fun PinColors.of(activity: GuideActivity): Color = when (activity) {
    GuideActivity.HUNT -> hunt
    GuideActivity.FISH -> fish
    GuideActivity.BOTH -> both
}

/**
 * The mockup's pin (design/mockup/Main.dc.html) as a bitmap: a teardrop in [fill], outlined and holed in [outline],
 * [density] pixels per dp. Its tip is at the bottom centre, where Google Maps anchors a marker by default.
 */
internal fun pinBitmap(fill: Color, outline: Color, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap((PIN_WIDTH * density).toInt(), (PIN_HEIGHT * density).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    // The mockup's SVG path, on its 28 × 38 grid.
    val drop = Path().apply {
        moveTo(14f, 1.5f)
        cubicTo(7.1f, 1.5f, 1.5f, 7f, 1.5f, 13.9f)
        cubicTo(1.5f, 23f, 14f, 36.5f, 14f, 36.5f)
        cubicTo(14f, 36.5f, 26.5f, 23f, 26.5f, 13.9f)
        cubicTo(26.5f, 7f, 20.9f, 1.5f, 14f, 1.5f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(drop, paint.apply { style = Paint.Style.FILL; color = fill.toArgb() })
    canvas.drawPath(drop, paint.apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = outline.toArgb() })
    canvas.drawCircle(14f, 14f, 4.5f, paint.apply { style = Paint.Style.FILL; color = outline.toArgb() })
    return bitmap
}

/** The pin's size in dp: the mockup's 28 × 38 grid. */
internal const val PIN_WIDTH = 28
internal const val PIN_HEIGHT = 38
