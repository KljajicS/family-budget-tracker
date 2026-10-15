package rs.budget.ui.insights

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LocalColors

/** Ring of [slices] as color and share (0..1). Empty slices draw the track only. */
@Composable
fun Donut(slices: List<Pair<Color, Float>>, description: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val stroke = with(LocalDensity.current) { 18.dp.toPx() }
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

data class BarPair(val label: String, val income: Float, val expenses: Float, val faded: Boolean)

/** Two bars per month, scaled to the tallest value. All zero draws nothing but the labels. */
@Composable
fun BarPairs(items: List<BarPair>, incomeColor: Color, expenseColor: Color, description: String, modifier: Modifier = Modifier) {
    val max = items.maxOfOrNull { maxOf(it.income, it.expenses) }?.takeIf { it > 0f } ?: 0f
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            if (items.isEmpty() || max == 0f) return@Canvas
            val slot = size.width / items.size
            val bar = (slot * 0.22f).coerceAtMost(14.dp.toPx())
            val gap = 4.dp.toPx()
            val radius = androidx.compose.ui.geometry.CornerRadius(bar / 2, bar / 2)
            items.forEachIndexed { i, it ->
                val center = slot * i + slot / 2
                val alpha = if (it.faded) 0.45f else 1f
                fun draw(value: Float, color: Color, x: Float) {
                    val h = size.height * (value / max)
                    if (h > 0f) drawRoundRect(color.copy(alpha = alpha), Offset(x, size.height - h), Size(bar, h), radius)
                }
                draw(it.income, incomeColor, center - gap / 2 - bar)
                draw(it.expenses, expenseColor, center + gap / 2)
            }
        }
        AxisLabels(items.map { it.label })
    }
}

/** Line with a soft area under it, last point marked. A flat line sits at the bottom. */
@Composable
fun TrendLine(points: List<Pair<String, Float>>, color: Color, description: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            if (points.size < 2) return@Canvas
            val min = points.minOf { it.second }
            val max = points.maxOf { it.second }
            val span = (max - min).takeIf { it > 0f } ?: 1f
            val dot = 5.dp.toPx()
            val stepX = (size.width - dot * 2) / (points.size - 1)
            fun y(v: Float) = dot + (size.height - dot * 2) * (1f - (v - min) / span)
            val line = Path()
            points.forEachIndexed { i, (_, v) ->
                val x = dot + stepX * i
                if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(dot + stepX * (points.size - 1), size.height)
                lineTo(dot, size.height)
                close()
            }
            drawPath(area, color.copy(alpha = 0.15f))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, dot, Offset(dot + stepX * (points.size - 1), y(points.last().second)))
        }
        AxisLabels(points.map { it.first })
    }
}

@Composable
private fun AxisLabels(labels: List<String>) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        labels.forEach { Text(it, style = CaptionStyle, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
    }
}
