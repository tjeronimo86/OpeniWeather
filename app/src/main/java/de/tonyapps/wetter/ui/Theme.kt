package de.tonyapps.wetter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import de.tonyapps.wetter.R
import de.tonyapps.wetter.sky.SkyParams

val Inter = FontFamily(
    Font(R.font.inter_200, FontWeight.ExtraLight),
    Font(R.font.inter_300, FontWeight.Light),
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
)

@Composable
fun WetterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Color.Black)) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides TextStyle(fontFamily = Inter, color = Color.White),
            content = content,
        )
    }
}

private val TEMP_STOPS = listOf(
    -12.0 to Color(0xFF8E7CFF),
    0.0 to Color(0xFF5CC8FA),
    10.0 to Color(0xFF7FE3B4),
    18.0 to Color(0xFFB8E36A),
    23.0 to Color(0xFFFFD84A),
    29.0 to Color(0xFFFFA63D),
    36.0 to Color(0xFFF2554B),
)

/** Farbskala der Temperaturbalken: gleiche Temperatur ergibt ueberall dieselbe Farbe. */
fun tempColor(t: Double): Color {
    if (t <= TEMP_STOPS.first().first) return TEMP_STOPS.first().second
    if (t >= TEMP_STOPS.last().first) return TEMP_STOPS.last().second
    for (i in 0 until TEMP_STOPS.size - 1) {
        val (a, ca) = TEMP_STOPS[i]
        val (b, cb) = TEMP_STOPS[i + 1]
        if (t <= b) return lerp(ca, cb, ((t - a) / (b - a)).toFloat())
    }
    return Color.White
}

/** Kartenfarbe passend zum Himmel: blaeulich am Tag, grau bei Wolken, fast schwarz nachts. */
fun cardTint(p: SkyParams): Color {
    val blue = Color(0xFF123F7D)
    val grey = Color(0xFF3A4450)
    val night = Color(0xFF0A1226)
    val day = lerp(blue, grey, (p.grey * p.cloud).coerceIn(0f, 1f))
    return lerp(night, day, p.day.coerceIn(0f, 1f)).copy(alpha = 0.34f)
}

val LocalCardTint = compositionLocalOf { Color(0x55123F7D) }

/** Rechteck mit runden Ecken, dessen Oberkante nach unten wandert. */
class TopInsetShape(private val inset: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(0f, inset.coerceAtMost(size.height), size.width, size.height, CornerRadius(radius)),
        )
}

/** Wo Karten beim Scrollen haengen bleiben. */
class StickyContext(val list: LazyListState, val pinnedPx: Float)

/**
 * Klebende Karte: Laeuft die Karte unter den Kopfbereich, bleibt ihre Ueberschrift stehen,
 * der Inhalt rutscht darunter weg, und zum Schluss blendet die Karte aus.
 * Alles passiert in graphicsLayer, also ohne Recomposition.
 */
@Composable
fun GlassCard(
    itemKey: Any,
    sticky: StickyContext,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val radius = with(density) { 16.dp.toPx() }
    var headerH by remember { mutableFloatStateOf(0f) }
    var cardH by remember { mutableFloatStateOf(0f) }

    fun overlap(): Float {
        val info = sticky.list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey } ?: return 0f
        return (sticky.pinnedPx - info.offset).coerceAtLeast(0f)
    }

    val click = if (onClick != null) {
        Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
    } else {
        Modifier
    }

    val fx = LocalCardFx.current
    val seed = remember(itemKey) { itemKey.hashCode() }
    // Aussen ungeschnitten, damit Spritzer ueber die Kante hinausfliegen duerfen
    Box(
        modifier
            .onSizeChanged { cardH = it.height.toFloat() }
            .drawWithContent {
                drawContent()
                if (fx != null) {
                    val o = overlap()
                    val fadeZone = (if (headerH > 0f) headerH else radius * 3f)
                    drawCardFx(fx, o, ((cardH - o) / fadeZone).coerceIn(0f, 1f), radius, seed)
                }
            },
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val o = overlap()
                clip = true
                shape = TopInsetShape(o, radius)
                val fadeZone = (if (headerH > 0f) headerH else radius * 3f)
                alpha = ((cardH - o) / fadeZone).coerceIn(0f, 1f)
            }
            .background(LocalCardTint.current)
            .then(click),
    ) {
        Column {
            if (header != null) {
                Box(
                    Modifier
                        .zIndex(1f)
                        .onSizeChanged { headerH = it.height.toFloat() }
                        .graphicsLayer { translationY = overlap().coerceAtMost((cardH - headerH).coerceAtLeast(0f)) },
                ) { header() }
            }
            Box(
                Modifier.graphicsLayer {
                    clip = true
                    shape = TopInsetShape(overlap(), 0f)
                },
            ) { content() }
        }
    }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.22f)))
}

@Composable
fun Dot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RectangleShapeCircle).background(color))
}

val RectangleShapeCircle: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.minDimension / 2)))
}
