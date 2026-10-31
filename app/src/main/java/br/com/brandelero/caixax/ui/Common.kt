package br.com.brandelero.caixax.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.brandelero.caixax.App
import br.com.brandelero.caixax.domain.yearMonth
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

object C {
    val bg = Color(0xFF0A0E14)
    val surface = Color(0xFF121821)
    val surface2 = Color(0xFF1A2230)
    val text = Color(0xFFE6EAF0)
    val muted = Color(0xFF8A94A6)
    val teal = Color(0xFF00D1A0)
    val blue = Color(0xFF3B82F6)
    val income = Color(0xFF2EE6A6)
    val expense = Color(0xFFFF6B7A)
    val warn = Color(0xFFFFB547)
    val hero = Brush.linearGradient(listOf(Color(0xFF00A88A), Color(0xFF1D4ED8)))
}

@Composable
fun CaixaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = C.teal, onPrimary = Color(0xFF00241B), secondary = C.blue,
        background = C.bg, onBackground = C.text, surface = C.surface, onSurface = C.text,
        surfaceVariant = C.surface2, onSurfaceVariant = C.muted,
        surfaceContainerLowest = C.bg, surfaceContainerLow = C.surface, surfaceContainer = C.surface,
        surfaceContainerHigh = C.surface2, surfaceContainerHighest = C.surface2,
        outline = Color(0xFF2A3444), outlineVariant = Color(0xFF222B38), error = C.expense,
    ),
    content = content,
)

@Composable
fun app() = LocalContext.current.applicationContext as App

val PT = Locale("pt", "BR")
private val BRL = NumberFormat.getCurrencyInstance(PT)
private val QTY = DecimalFormat("#,##0.########", DecimalFormatSymbols(PT))

fun money(cents: Long, hide: Boolean = false): String = if (hide) "R$ ••••" else BRL.format(cents / 100.0)
fun qty(v: Double, hide: Boolean = false): String = if (hide) "••••" else QTY.format(v)
fun centsText(c: Long) = "%d,%02d".format(c / 100, c % 100)
fun monthTitle(ym: Int) = yearMonth(ym).format(DateTimeFormatter.ofPattern("MMMM yyyy", PT)).replaceFirstChar { it.uppercase() }
fun monthShort(ym: Int) = yearMonth(ym).format(DateTimeFormatter.ofPattern("MMM/yy", PT))

val CategoryIcons: Map<String, ImageVector> = linkedMapOf(
    "home" to Icons.Rounded.Home, "cart" to Icons.Rounded.ShoppingCart, "food" to Icons.Rounded.Restaurant,
    "car" to Icons.Rounded.DirectionsCar, "health" to Icons.Rounded.LocalHospital, "school" to Icons.Rounded.School,
    "bolt" to Icons.Rounded.Bolt, "wifi" to Icons.Rounded.Wifi, "fun" to Icons.Rounded.SportsEsports,
    "pets" to Icons.Rounded.Pets, "shirt" to Icons.Rounded.Checkroom, "work" to Icons.Rounded.Work,
    "money" to Icons.Rounded.Payments, "star" to Icons.Rounded.Star, "gift" to Icons.Rounded.CardGiftcard,
    "savings" to Icons.Rounded.Savings, "travel" to Icons.Rounded.Flight, "other" to Icons.Rounded.Category,
)

fun categoryIcon(key: String?) = CategoryIcons[key] ?: Icons.Rounded.Category

val Palette = listOf(
    0xFF00D1A0, 0xFF3B82F6, 0xFFFF6B7A, 0xFFFFB547, 0xFFA78BFA,
    0xFF22D3EE, 0xFFF472B6, 0xFF84CC16, 0xFFFB923C, 0xFF94A3B8,
)

@Composable
fun IconBubble(icon: ImageVector, color: Color) = Box(
    Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
    contentAlignment = Alignment.Center,
) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }

@Composable
fun EyeButton() {
    val prefs = app().prefs
    IconButton(onClick = { prefs.hide = !prefs.hide }) {
        Icon(
            if (prefs.hide) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (prefs.hide) "Mostrar valores" else "Ocultar valores",
            tint = C.muted,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = C.muted,
    modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
)

/** Diálogo com uma lista de opções; cada uma executa sua ação e fecha. */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
        Column {
            options.forEach { (label, action) ->
                TextButton(onClick = { action(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(label, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    containerColor = C.surface2,
)

@Composable
fun Donut(slices: List<Pair<Color, Long>>, modifier: Modifier, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.13f
            val gap = if (slices.size > 1) 2f else 0f
            var angle = -90f
            slices.forEach { (color, value) ->
                val sweep = 360f * value / total
                drawArc(
                    color, angle + gap / 2, (sweep - gap).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                angle += sweep
            }
        }
        center()
    }
}

@Composable
fun Sparkline(points: List<Long>, color: Color, modifier: Modifier) = Canvas(modifier) {
    if (points.size < 2) return@Canvas
    val max = points.max().toFloat()
    val min = minOf(0L, points.min()).toFloat()
    val range = (max - min).coerceAtLeast(1f)
    val dx = size.width / (points.size - 1)
    val line = Path()
    points.forEachIndexed { i, v ->
        val x = i * dx
        val y = size.height - (v - min) / range * size.height
        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
    drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
