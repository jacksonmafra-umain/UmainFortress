package com.umain.fortress.ui.screens.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme

/**
 * "Scan QR to pay" surface. Renders an ink-black square with rounded corner brackets and a
 * scanning line. Camera wiring is not part of this UI scaffold — that lands when we adopt
 * CameraX in a follow-up.
 *
 * Reference: Vault scan mock — black framing region with corner brackets + lavender wash bar.
 */
@Composable
fun ScanQrScreen(
    onBack: () -> Unit,
    onScanned: (payload: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = FortressIcons.Back,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Scan QR to Pay",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
        }

        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.extraLarge,
            color = FortressTheme.colors.cardInk,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CornerBrackets(modifier = Modifier.padding(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(Color.White, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "QR",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.Black,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Scan a pay-now QR to pay or open Fortress To Connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FooterAction(label = "Pay", icon = FortressIcons.Send, onClick = { onScanned("demo:pay") })
                FooterAction(label = "Receive", icon = FortressIcons.Receive, onClick = { onScanned("demo:receive") })
            }
        }
    }
}

@Composable
private fun CornerBrackets(modifier: Modifier = Modifier) {
    val color = Color.White
    Canvas(modifier = modifier.fillMaxSize()) {
        val arm = 28f
        val stroke = Stroke(width = 6f, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), stroke.width)
        drawLine(color, Offset(0f, 0f), Offset(0f, arm), stroke.width)
        drawLine(color, Offset(size.width - arm, 0f), Offset(size.width, 0f), stroke.width)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, arm), stroke.width)
        drawLine(color, Offset(0f, size.height), Offset(arm, size.height), stroke.width)
        drawLine(color, Offset(0f, size.height - arm), Offset(0f, size.height), stroke.width)
        drawLine(color, Offset(size.width - arm, size.height), Offset(size.width, size.height), stroke.width)
        drawLine(color, Offset(size.width, size.height - arm), Offset(size.width, size.height), stroke.width)
    }
}

@Composable
private fun FooterAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
