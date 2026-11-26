package pw.c0re.stream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import pw.c0re.stream.ui.theme.C0reStreamCyan
import pw.c0re.stream.ui.theme.C0reStreamViolet

// C0reStream Logo-Mark: Kreis in Brand-Farbverlauf mit ausgestanztem Play-Dreieck
@Composable
fun C0reStreamMark(size: Int = 28) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(C0reStreamCyan, C0reStreamViolet))),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u25B6", style = MaterialTheme.typography.labelSmall)
    }
}

// C0reStream Wortmarke mit Logo
@Composable
fun C0reStreamWordmark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        C0reStreamMark(if (compact) 24 else 34)
        Text(
            "C0reStream TV",
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
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
        Text("Powered by C0reStream", style = MaterialTheme.typography.labelSmall)
    }
}
