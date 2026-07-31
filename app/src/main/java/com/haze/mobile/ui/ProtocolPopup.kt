package com.haze.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.haze.mobile.ui.theme.HazeColors
import kotlin.math.PI
import kotlin.math.sin

private val Green = Color(0xFF34C759)
private val PanelBg     = Color(0xFC070709)   // rgba(7,7,9,252)
private val PanelBorder = Color(0xFF34343A)   // rgba(52,52,58,255)
private val SepColor    = Color(0xC82D2D32)   // rgba(45,45,50,200)
private val BadgeBg     = Color(0xFF020905)
private val BadgeBorder = Color(0xFF0A2E14)

/**
 * Full-screen dim overlay + centred panel — Compose port of the desktop
 * `_ProtocolPopup`. Shows the animated Tor circuit and protocol details.
 */
@Composable
fun ProtocolPopup(
    onionAddress: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Dim backdrop — tap outside the panel to close
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(PanelBg, RoundedCornerShape(20.dp))
                    .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
                    // Swallow taps on the panel so they don't close the dialog
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                // ── Header ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "HAZE PROTOCOL",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "E2E ENCRYPTED",
                        color = Green,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .background(BadgeBg, RoundedCornerShape(6.dp))
                            .border(1.dp, BadgeBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xC8161619), CircleShape)
                            .border(1.dp, Color(0xFF2E2E32), CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = HazeColors.Text3, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Separator()
                Spacer(Modifier.height(16.dp))

                // ── Circuit visualization ──
                CircuitDiagram()

                Spacer(Modifier.height(16.dp))
                Separator()
                Spacer(Modifier.height(18.dp))

                // ── Info grid ──
                SectionLabel("ENCRYPTION")
                InfoRow("Cipher", "ChaCha20-Poly1305", green = true)
                InfoRow("Key Exchange", "X25519 ECDH", green = true)
                InfoRow("Key Derivation", "HKDF · SHA-256", green = true)
                InfoRow("Session Keys", "Ephemeral", green = true)

                Spacer(Modifier.height(14.dp))
                SectionLabel("CONNECTION")
                InfoRow("Transport", "Tor Hidden Service")
                if (!onionAddress.isNullOrBlank()) {
                    val a = onionAddress
                    val short = if (a.length > 28) a.take(20) + "…" + a.takeLast(6) else a
                    InfoRow("Host Onion", short)
                }
                InfoRow("Routing", "3-hop Tor circuit")

                Spacer(Modifier.height(14.dp))
                SectionLabel("PRIVACY")
                InfoRow("Logs", "None · Zero retention", green = true)
                InfoRow("Identity", "Anonymous via Tor", green = true)
                InfoRow("Panic", "Instant wipe on exit")
            }
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SepColor))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = HazeColors.Text2,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.5.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(key: String, value: String, green: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, color = HazeColors.Text2, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = if (green) Green else Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Animated Tor circuit — YOU → GUARD → RELAY → SERVICE → PEER, with a green
 * data pulse travelling across the encrypted tunnel. Port of `_CircuitWidget`.
 */
@Composable
private fun CircuitDiagram() {
    data class Node(val label: String, val sub: String, val isTor: Boolean)
    val nodes = listOf(
        Node("YOU", "you", false),
        Node("GUARD", "guard node", true),
        Node("RELAY", "relay node", true),
        Node("SERVICE", "hidden svc", true),
        Node("PEER", "peer", false),
    )

    val transition = rememberInfiniteTransition(label = "circuit")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
    ) {
        val w = size.width
        val h = size.height
        val n = nodes.size
        val nw = 62.dp.toPx()
        val nh = 26.dp.toPx()
        val pad = nw / 2 + 6.dp.toPx()
        val usable = w - 2 * pad
        val step = usable / (n - 1)
        val cy = h / 2 - 6.dp.toPx()
        val xs = FloatArray(n) { pad + it * step }

        // Dotted connecting lines + chevrons
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (i in 0 until n - 1) {
            val x1 = xs[i] + nw / 2
            val x2 = xs[i + 1] - nw / 2
            drawLine(
                color = Green.copy(alpha = 0.35f),
                start = Offset(x1, cy),
                end = Offset(x2, cy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
            val mx = (x1 + x2) / 2
            drawLine(Green.copy(alpha = 0.5f), Offset(mx - 5.dp.toPx(), cy - 4.dp.toPx()), Offset(mx, cy), 1.dp.toPx())
            drawLine(Green.copy(alpha = 0.5f), Offset(mx - 5.dp.toPx(), cy + 4.dp.toPx()), Offset(mx, cy), 1.dp.toPx())
        }

        // Travelling data pulse
        val pxPos = xs[0] + t * (xs[n - 1] - xs[0])
        val pulse = 0.5f + 0.5f * sin(t.toDouble() * PI * 4).toFloat()
        val r = 3.5.dp.toPx() + pulse * 1.5.dp.toPx()
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Green.copy(alpha = 0.2f * pulse), Color.Transparent),
                center = Offset(pxPos, cy),
                radius = 14.dp.toPx(),
            ),
            radius = 14.dp.toPx(),
            center = Offset(pxPos, cy),
        )
        drawCircle(Green.copy(alpha = 0.63f + 0.37f * pulse), r, Offset(pxPos, cy))

        // Nodes
        for (i in nodes.indices) {
            val node = nodes[i]
            val x = xs[i]
            val rectTL = Offset(x - nw / 2, cy - nh / 2)
            val rectSize = Size(nw, nh)
            val bg = if (node.isTor) Color(0xFF06160A) else Color(0xFF101014)
            val border = if (node.isTor) Color(0xFF1C582C) else Color(0xFF323238)
            val tc = if (node.isTor) Green else Color(0xFFAAAAB2)

            drawRoundRect(
                color = bg,
                topLeft = rectTL,
                size = rectSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            )
            drawRoundRect(
                color = border,
                topLeft = rectTL,
                size = rectSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )

            // Node label (centred)
            drawCenteredText(textMeasurer, node.label, Offset(x, cy), tc, 8.sp.value * density, bold = true)
            // Sub-label below
            drawCenteredText(
                textMeasurer, node.sub,
                Offset(x, cy + nh / 2 + 11.dp.toPx()),
                Color(0xFF646468), 7.sp.value * density, bold = false,
            )
        }
    }
}

private fun DrawScope.drawCenteredText(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    fontSizePx: Float,
    bold: Boolean,
) {
    val style = androidx.compose.ui.text.TextStyle(
        color = color,
        fontSize = androidx.compose.ui.unit.TextUnit(fontSizePx / density, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = if (bold) 1.sp else 0.sp,
    )
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
    )
}
