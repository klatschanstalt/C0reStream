package pw.c0re.stream.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pw.c0re.stream.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.c0re.stream.ui.theme.BrandButtonShape
import pw.c0re.stream.ui.theme.BrandCardShape
import pw.c0re.stream.ui.theme.BrandMotion
import pw.c0re.stream.ui.theme.BrandPillShape
import pw.c0re.stream.ui.theme.LocalBrandTypography

// The public API below is intentionally stable: other modules call these exact names.

@Composable
fun BrandBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalBrandTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.backdrop),
    ) {
        // Static radial gradients only: no blur, no infinite animation.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .background(tokens.glow),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(360.dp)
                .background(tokens.glowSecondary),
        )
        content()
    }
}

@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalBrandTokens.current
    val reducedMotion = rememberBrandReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (onClick != null && pressed && !reducedMotion) BrandMotion.PressScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = BrandMotion.spec(reducedMotion, BrandMotion.DurationFast),
        label = "BrandCardPress",
    )

    val clickModifier = if (onClick != null) {
        Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { role = Role.Button }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = tokens.spacing.touchTarget)
            .clip(BrandCardShape)
            .background(tokens.cardBackground)
            .border(1.dp, tokens.cardBorder, BrandCardShape)
            .then(clickModifier)
            .padding(tokens.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        content = content,
    )
}

@Composable
fun BrandPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val tokens = LocalBrandTokens.current
    val disabledBackground = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant),
    )
    Box(
        modifier = modifier
            .heightIn(min = tokens.spacing.touchTarget)
            .clip(BrandButtonShape)
            .background(if (enabled) tokens.primaryGradient else disabledBackground),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = BrandButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = tokens.onAccent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            ),
        ) {
            BrandButtonContent(text = text, icon = icon)
        }
    }
}

@Composable
fun BrandSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val tokens = LocalBrandTokens.current
    val borderColor = if (enabled) tokens.accentSecondary else tokens.states.disabled
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = tokens.spacing.touchTarget),
        enabled = enabled,
        shape = BrandButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = tokens.accentSecondary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = tokens.states.disabled,
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        BrandButtonContent(text = text, icon = icon)
    }
}

@Composable
private fun RowScope.BrandButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(
        text = text,
        style = LocalBrandTypography.current.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun BrandSectionTitle(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalBrandTokens.current
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(BrandPillShape)
                .background(tokens.primaryGradient),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = LocalBrandTypography.current.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BrandTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = LocalBrandTokens.current
    Column(modifier = Modifier.fillMaxWidth().background(tokens.topBarBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = tokens.spacing.topBarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(tokens.spacing.touchTarget),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier.size(tokens.spacing.iconSize),
                    )
                }
            }
            Text(
                text = title,
                style = LocalBrandTypography.current.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = tokens.spacing.sm),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                content = actions,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp, max = 1.dp)
                .background(tokens.cardBorder),
        )
    }
}

@Composable
fun BrandEmptyState(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = LocalBrandTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = tokens.spacing.lg,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = message,
            style = LocalBrandTypography.current.statusBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            BrandPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun BrandLoading(label: String? = null) {
    val tokens = LocalBrandTokens.current
    val description = label ?: stringResource(R.string.common_loading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.xl)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = tokens.spacing.md,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        if (rememberBrandReducedMotion()) {
            // Static ring: no indefinite animation when the user asked for less motion.
            Box(Modifier.size(32.dp).clip(CircleShape).border(3.dp, tokens.accent))
        } else {
            CircularProgressIndicator(color = tokens.accent)
        }
        if (label != null) {
            Text(
                text = label,
                style = LocalBrandTypography.current.statusLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BrandBadge(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalBrandTokens.current
    Box(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(BrandPillShape)
            .background(tokens.badgeBackground)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LocalBrandTypography.current.labelSmall,
            color = tokens.onAccent,
            maxLines = 1,
        )
    }
}
