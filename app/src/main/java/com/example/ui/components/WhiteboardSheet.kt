package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WhiteboardStroke
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardSheet(
    targetLabel: String,
    strokes: List<WhiteboardStroke>,
    onAddStroke: (WhiteboardStroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onShareToChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val brushColors = listOf(0xFFFFFFFFL, 0xFF5865F2L, 0xFF23A55AL, 0xFFF0B232L, 0xFFEB459EL, 0xFFED4245L)
    var selectedColorHex by remember { mutableLongStateOf(0xFF5865F2L) }
    var brushWidth by remember { mutableFloatStateOf(7f) }
    var activePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("whiteboard_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brush, contentDescription = null, tint = palette.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Tableau Blanc • $targetLabel", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Dessinez ensemble en temps réel (${strokes.size} tracés)", color = palette.textMuted, fontSize = 11.sp)
                    }
                }
                Row {
                    IconButton(onClick = onUndo, enabled = strokes.isNotEmpty(), modifier = Modifier.testTag("whiteboard_undo")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Annuler dernier tracé", tint = palette.textPrimary)
                    }
                    IconButton(onClick = onClear, enabled = strokes.isNotEmpty(), modifier = Modifier.testTag("whiteboard_clear")) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Tout effacer", tint = DiscordRed)
                    }
                }
            }

            // Drawing Canvas
            Surface(
                color = Color(0xFF14161D),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, palette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .testTag("whiteboard_canvas")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedColorHex, brushWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activePoints = listOf(offset.x to offset.y)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activePoints = activePoints + (change.position.x to change.position.y)
                                },
                                onDragEnd = {
                                    if (activePoints.size >= 2) {
                                        onAddStroke(
                                            WhiteboardStroke(
                                                points = activePoints,
                                                colorHex = selectedColorHex,
                                                strokeWidth = brushWidth
                                            )
                                        )
                                    }
                                    activePoints = emptyList()
                                },
                                onDragCancel = {
                                    activePoints = emptyList()
                                }
                            )
                        }
                ) {
                    val drawStroke = { pts: List<Pair<Float, Float>>, color: Color, width: Float ->
                        if (pts.size >= 2) {
                            val path = Path().apply {
                                moveTo(pts.first().first, pts.first().second)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].first, pts[i].second)
                                }
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        } else if (pts.size == 1) {
                            drawCircle(color = color, radius = width / 2f, center = Offset(pts[0].first, pts[0].second))
                        }
                    }

                    strokes.forEach { st ->
                        drawStroke(st.points, Color(st.colorHex), st.strokeWidth)
                    }
                    if (activePoints.isNotEmpty()) {
                        drawStroke(activePoints, Color(selectedColorHex), brushWidth)
                    }
                }
            }

            // Color Palette & Thickness
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    brushColors.forEach { hex ->
                        val isSel = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(if (isSel) Modifier.border(2.5.dp, palette.accent, CircleShape) else Modifier)
                                .clickable { selectedColorHex = hex }
                                .testTag("whiteboard_color_$hex")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4f to "Fin", 8f to "Moyen", 14f to "Épais").forEach { (w, label) ->
                        FilterChip(
                            selected = brushWidth == w,
                            onClick = { brushWidth = w },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onShareToChat()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.fillMaxWidth().testTag("whiteboard_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Partager le tableau dans le salon", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
