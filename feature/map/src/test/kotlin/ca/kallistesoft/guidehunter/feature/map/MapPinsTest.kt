package ca.kallistesoft.guidehunter.feature.map

import androidx.compose.ui.graphics.toArgb
import ca.kallistesoft.guidehunter.core.designsystem.theme.DarkPinColors
import ca.kallistesoft.guidehunter.core.designsystem.theme.LightPinColors
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapPinsTest {
    @Test
    fun `each activity has its own pin colour, in both themes`() {
        assertEquals(LightPinColors.hunt, LightPinColors.of(GuideActivity.HUNT))
        assertEquals(LightPinColors.fish, LightPinColors.of(GuideActivity.FISH))
        assertEquals(DarkPinColors.both, DarkPinColors.of(GuideActivity.BOTH))
    }

    @Test
    fun `the pin is a filled teardrop with a hole, on a transparent background, at the screen's density`() {
        val pin = pinBitmap(DarkPinColors.fish, DarkPinColors.outline, density = 2f)
        assertEquals(PIN_WIDTH * 2, pin.width)
        assertEquals(PIN_HEIGHT * 2, pin.height)
        assertEquals("body", DarkPinColors.fish.toArgb(), pin.getPixel(28, 52))
        assertEquals("hole", DarkPinColors.outline.toArgb(), pin.getPixel(28, 28))
        assertEquals("corner", 0, pin.getPixel(1, 74))
    }

    @Test
    fun `a featured pin is the pin in a gold ring, with a gold star, its tip at the bottom centre`() {
        val pin = featuredPinBitmap(LightPinColors.hunt, LightPinColors.outline, LightPinColors.featured, density = 1f)
        assertEquals(FEATURED_PIN_WIDTH to FEATURED_PIN_HEIGHT, pin.width to pin.height)
        assertEquals("body", LightPinColors.hunt.toArgb(), pin.getPixel(26, 38))
        assertEquals("hole", LightPinColors.outline.toArgb(), pin.getPixel(26, 26))
        assertEquals("ring", LightPinColors.featured.ring.toArgb(), pin.getPixel(26 - 18, 26))
        assertEquals("star badge", LightPinColors.featured.ring.toArgb(), pin.getPixel(49, 9))
        assertEquals("star", LightPinColors.outline.toArgb(), pin.getPixel(43, 9))
        assertEquals("corner", 0, pin.getPixel(1, 49))
    }
}
