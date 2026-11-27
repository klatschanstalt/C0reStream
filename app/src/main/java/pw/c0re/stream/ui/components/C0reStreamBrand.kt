package pw.c0re.stream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// C0reStream logo: the open gradient ring, play mark and accent nodes from
// assets/branding/logo-transparent.svg, drawn with theme tokens so every brand
// palette (Ham3l cyan/violet and Ember) stays consistent with Material colors.
@Composable
fun C0reStreamMark(size: Int = 28) {
    val tokens = LocalBrandTokens.current
    // The mark sits next to a text label wherever it is used, so it is
    // decorative for screen readers rather than a second, unlabeled control.
    Canvas(Modifier.size(size.dp).clearAndSetSemantics { }) {
        val dimension = size.toPx()
        val stroke = dimension * 0.09f
        val inset = stroke * 0.7f
        val arcSize = Size(dimension - inset * 2f, dimension - inset * 2f)
        val gradient = tokens.primaryGradient

        drawArc(
            brush = gradient,
            startAngle = 38f,
            sweepAngle = 302f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val play = Path().apply {
            moveTo(dimension * 0.41f, dimension * 0.33f)
            lineTo(dimension * 0.41f, dimension * 0.67f)
            lineTo(dimension * 0.69f, dimension * 0.50f)
            close()
        }
        drawPath(path = play, brush = gradient)

        val nodeRadius = stroke * 0.72f
        drawCircle(
            color = tokens.accent,
            radius = nodeRadius,
            center = Offset(dimension * 0.24f, dimension * 0.24f),
        )
        drawCircle(
            color = tokens.accentSecondary,
            radius = nodeRadius,
            center = Offset(dimension * 0.78f, dimension * 0.56f),
        )
    }
}

@Composable
fun C0reStreamWordmark(compact: Boolean = false) {
    val tokens = LocalBrandTokens.current
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        C0reStreamMark(if (compact) 24 else 34)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onBackground)) { append("C0re") }
                withStyle(SpanStyle(color = tokens.accent)) { append("Stream") }
                withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(" TV") }
            },
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun PoweredByFooter() {
    Row(
        Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        C0reStreamMark(16)
        Text(
            "Powered by C0reStream",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
