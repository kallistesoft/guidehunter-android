package ca.kallistesoft.guidehunter.core.ads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import ca.kallistesoft.guidehunter.core.designsystem.theme.AdLabelColor
import ca.kallistesoft.guidehunter.core.designsystem.theme.OnAdLabelColor
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.awaitCancellation

/**
 * One native ad as a card, styled like the offer cards, for the app's lists (never the map or a guide's offer). It
 * loads its own ad when it enters the screen and destroys it when it leaves; with no ad (ads off, no consent, no
 * fill) it draws nothing at all, so the list closes up with no gap.
 *
 * [ads] comes from the screen's ViewModel, which injects [NativeAds] (Hilt binds it to AdMob), and hands it to the
 * screen's content: `NativeAdCard(viewModel.nativeAds)`. Tests build the ViewModel with `FakeNativeAds` from
 * `:core:testing`, which serves a fake ad (or none).
 */
@Composable
fun NativeAdCard(ads: NativeAds, modifier: Modifier = Modifier) {
    val ad = rememberNativeAd(ads) ?: return
    if (ad is GoogleNativeAd) {
        AdmobNativeAdView(ad, modifier)
    } else {
        NativeAdContent(ad.headline, ad.body, ad.callToAction, ad.icon, modifier)
    }
}

/** The ad [ads] serves while this is on screen: null until it loads, or if there's none. Destroyed on leaving. */
@Composable
private fun rememberNativeAd(ads: NativeAds): NativeAdHandle? {
    var ad by remember(ads) { mutableStateOf<NativeAdHandle?>(null) }
    LaunchedEffect(ads) {
        val loaded = ads.load() ?: return@LaunchedEffect
        ad = loaded
        try {
            awaitCancellation()
        } finally {
            loaded.destroy()
        }
    }
    return ad
}

/** The parts of the ad AdMob tracks clicks on: each one is its own view, registered with the [NativeAdView]. */
internal enum class AdAsset { ICON, HEADLINE, BODY, CALL_TO_ACTION }

/**
 * The card inside AdMob's [NativeAdView], which counts the impression and turns taps on the assets into the ad's
 * click. Each asset is a small Compose view of its own (AdMob needs one view per asset); the ad is set once they're
 * all registered.
 */
@Composable
private fun AdmobNativeAdView(ad: GoogleNativeAd, modifier: Modifier) {
    val context = LocalContext.current
    val adView = remember(ad) { NativeAdView(context) }
    AndroidView(
        factory = {
            adView.apply {
                addView(
                    ComposeView(it).apply {
                        setContent {
                            NativeAdContent(ad.headline, ad.body, ad.callToAction, ad.icon) { asset, content ->
                                AdAssetView(adView, asset, content)
                            }
                            LaunchedEffect(ad) { adView.setNativeAd(ad.ad) }
                        }
                    },
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun AdAssetView(adView: NativeAdView, asset: AdAsset, content: @Composable () -> Unit) {
    AndroidView(
        factory = {
            ComposeView(it).also { view ->
                when (asset) {
                    AdAsset.ICON -> adView.iconView = view
                    AdAsset.HEADLINE -> adView.headlineView = view
                    AdAsset.BODY -> adView.bodyView = view
                    AdAsset.CALL_TO_ACTION -> adView.callToActionView = view
                }
            }
        },
        update = { it.setContent(content) },
    )
}

/**
 * The ad card's look (design/mockup, GuideHome): the advertiser's icon, the "Ad" label and headline, the body, and the
 * call to action. [asset] wraps each part AdMob tracks; as is, it draws them plainly (a fake ad, previews).
 */
@Composable
internal fun NativeAdContent(
    headline: String,
    body: String?,
    callToAction: String?,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
    asset: @Composable (AdAsset, @Composable () -> Unit) -> Unit = { _, content -> content() },
) {
    OutlinedCard(modifier.fillMaxWidth().testTag("native-ad")) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                asset(AdAsset.ICON) {
                    Image(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.adIcon).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    AdLabel()
                    asset(AdAsset.HEADLINE) {
                        Text(headline, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (body != null) {
                    asset(AdAsset.BODY) {
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (callToAction != null) asset(AdAsset.CALL_TO_ACTION) { CallToAction(callToAction) }
        }
    }
}

/** "Ad" / "Annonce": every native ad says it's one (AdMob's policy). */
@Composable
private fun AdLabel() {
    Text(
        stringResource(R.string.ad_label),
        style = MaterialTheme.typography.labelSmall,
        color = OnAdLabelColor,
        modifier = Modifier
            .background(AdLabelColor, RoundedCornerShape(Spacing.xs))
            .padding(horizontal = Spacing.s - Spacing.xxs)
            .testTag("ad-label"),
    )
}

/**
 * Looks like an outlined button, but isn't clickable in Compose: the tap goes through to its view, which AdMob
 * registered as the call to action.
 */
@Composable
private fun CallToAction(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(Sizes.border, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.m), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.testTag("ad-call-to-action"))
        }
    }
}
