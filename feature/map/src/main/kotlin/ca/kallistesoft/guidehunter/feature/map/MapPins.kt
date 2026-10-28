package ca.kallistesoft.guidehunter.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ca.kallistesoft.guidehunter.core.designsystem.theme.FeaturedColors
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
    drawPin(canvas, fill, outline)
    return bitmap
}

/**
 * A featured (boosted) guide's pin, the mockup's: the same teardrop in a gold [featured] ring, with a gold star badge
 * at its top right. Wider and taller than a pin ([FEATURED_PIN_WIDTH] × [FEATURED_PIN_HEIGHT]), its tip still at
 * the bottom centre.
 */
internal fun featuredPinBitmap(fill: Color, outline: Color, featured: FeaturedColors, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap((FEATURED_PIN_WIDTH * density).toInt(), (FEATURED_PIN_HEIGHT * density).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // The ring: 40 across with a 3 wide border, centred over the pin's head.
    val centreX = FEATURED_PIN_WIDTH / 2f
    canvas.drawCircle(centreX, RING_CENTRE_Y, RING_RADIUS, paint.apply { style = Paint.Style.FILL; color = featured.tint.toArgb() })
    canvas.drawCircle(centreX, RING_CENTRE_Y, RING_RADIUS, paint.apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = featured.ring.toArgb() })
    canvas.save()
    canvas.translate(centreX - PIN_WIDTH / 2f, (FEATURED_PIN_HEIGHT - PIN_HEIGHT).toFloat())
    drawPin(canvas, fill, outline)
    canvas.restore()
    // The star badge, 18 across, over the ring's top right.
    canvas.drawCircle(STAR_CENTRE_X, STAR_CENTRE_Y, STAR_RADIUS, paint.apply { style = Paint.Style.FILL; color = featured.ring.toArgb() })
    canvas.save()
    // The mockup's star, on its 24 grid, drawn 12 across.
    canvas.translate(STAR_CENTRE_X - 6f, STAR_CENTRE_Y - 6f)
    canvas.scale(0.5f, 0.5f)
    canvas.drawPath(star(), paint.apply { style = Paint.Style.FILL; color = outline.toArgb() })
    canvas.restore()
    return bitmap
}

/** The mockup's SVG pin, on its 28 × 38 grid. */
private fun drawPin(canvas: Canvas, fill: Color, outline: Color) {
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
}

/** The mockup's star ("M12 2.5l2.9 6.1 6.6.8-4.9 4.6 1.3 6.6L12 17.3l-5.9 3.3 1.3-6.6-4.9-4.6 6.6-.8z"). */
private fun star() = Path().apply {
    moveTo(12f, 2.5f)
    rLineTo(2.9f, 6.1f)
    rLineTo(6.6f, 0.8f)
    rLineTo(-4.9f, 4.6f)
    rLineTo(1.3f, 6.6f)
    lineTo(12f, 17.3f)
    rLineTo(-5.9f, 3.3f)
    rLineTo(1.3f, -6.6f)
    rLineTo(-4.9f, -4.6f)
    rLineTo(6.6f, -0.8f)
    close()
}

/** The pin's size in dp: the mockup's 28 × 38 grid. */
internal const val PIN_WIDTH = 28
internal const val PIN_HEIGHT = 38

/** A featured pin's size in dp: the pin in its ring (40 across) and the star badge sticking out at the top right. */
internal const val FEATURED_PIN_WIDTH = 52
internal const val FEATURED_PIN_HEIGHT = 50
private const val RING_CENTRE_Y = 26f
private const val RING_RADIUS = 18.5f
private const val STAR_CENTRE_X = 43f
private const val STAR_CENTRE_Y = 9f
private const val STAR_RADIUS = 9f
