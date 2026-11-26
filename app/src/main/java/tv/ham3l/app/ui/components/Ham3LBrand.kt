package tv.ham3l.app.ui.components

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
import tv.ham3l.app.ui.theme.Ham3LCyan
import tv.ham3l.app.ui.theme.Ham3LViolet

// Ham3L Logo-Mark: Kreis in Brand-Farbverlauf mit ausgestanztem Play-Dreieck
@Composable
fun Ham3LMark(size: Int = 28) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Ham3LCyan, Ham3LViolet))),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u25B6", style = MaterialTheme.typography.labelSmall)
    }
}

// Ham3L Wortmarke mit Logo
@Composable
fun Ham3LWordmark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ham3LMark(if (compact) 24 else 34)
        Text(
            "Ham3L TV",
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
        Ham3LMark(16)
        Text("Powered by Ham3L", style = MaterialTheme.typography.labelSmall)
    }
}
