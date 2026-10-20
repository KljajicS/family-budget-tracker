package app.kinjar.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kinjar.ui.theme.CaptionStyle
import app.kinjar.ui.theme.LocalColors
import app.kinjar.ui.theme.sans
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun Donut(slices: List<Pair<Color, Float>>, description: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val stroke = with(LocalDensity.current) { 22.dp.toPx() }
    Canvas(modifier.semantics { contentDescription = description }) {
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(c.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        var start = -90f
        slices.forEach { (color, share) ->
            val sweep = 360f * share.coerceIn(0f, 1f)
            drawArc(color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}

data class BarGroup(val label: String, val income: Float, val expenses: Float, val description: String)

/** Rounds up to 1, 2, 2.5 or 5 times a power of ten so the axis reads in round numbers. */
fun niceMax(value: Float): Float {
    if (value <= 0f) return 1f
    val magnitude = 10f.pow(floor(log10(value)))
    return listOf(1f, 2f, 2.5f, 5f, 10f).map { it * magnitude }.first { it >= value }
}

/** Each month is its own tap target so TalkBack and touch both reach it; [axis] runs top to bottom. */
@Composable
fun BarChart(
    groups: List<BarGroup>,
    max: Float,
    axis: List<String>,
    selected: Int?,
    emphasized: Int,
    onSelect: (Int) -> Unit,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val chartHeight = 150.dp
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.height(chartHeight).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            axis.forEach { Text(it, style = CaptionStyle, color = c.muted) }
        }
        groups.forEachIndexed { i, g ->
            val active = i == selected
            Column(
                Modifier.weight(1f)
                    .semantics { contentDescription = g.description; this.selected = active }
                    .clickable(role = Role.Button) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                    if (active) drawRoundRect(c.primary.copy(alpha = 0.08f), cornerRadius = CornerRadius(8.dp.toPx()))
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    listOf(0f, 0.5f, 1f).forEach { level ->
                        val y = size.height * (1f - level)
                        drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = if (level > 0f) dash else null)
                    }
                    val bar = (size.width * 0.28f).coerceAtMost(16.dp.toPx())
                    val gap = 3.dp.toPx()
                    val center = size.width / 2
                    fun draw(value: Float, color: Color, x: Float) {
                        val h = size.height * (value / max).coerceIn(0f, 1f)
                        if (h > 0f) drawRoundRect(color, Offset(x, size.height - h), Size(bar, h), CornerRadius(3.dp.toPx()))
                    }
                    draw(g.income, incomeColor, center - gap / 2 - bar)
                    draw(g.expenses, expenseColor, center + gap / 2)
                }
                Text(
                    g.label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                    style = if (i == emphasized || active) sans(12, FontWeight.SemiBold) else CaptionStyle,
                    color = if (i == emphasized || active) c.text else c.muted,
                )
            }
        }
    }
}
