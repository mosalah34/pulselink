package com.pulselink.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulselink.app.data.model.VitalReading
import com.pulselink.app.ui.theme.HeartRateRed
import com.pulselink.app.ui.theme.NavyCard
import com.pulselink.app.ui.theme.NavyCardBorder
import com.pulselink.app.ui.theme.TempCyan
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiveDualAxisChart(
    modifier: Modifier = Modifier,
    readings: List<VitalReading>,
    maxPoints: Int = 40
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(NavyCardBorder.copy(alpha = 0.8f), NavyCardBorder.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with Dual Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMBINED TELEMETRY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // HR Legend
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HeartRateRed)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "HR (BPM)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = HeartRateRed
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    // Temp Legend
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TempCyan)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TEMP (°C)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TempCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart area with left & right Y axes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (readings.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting real HC-05 sensor data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                } else {
                    // Ensure points are sorted chronologically (earliest to latest)
                    val points = readings.takeLast(maxPoints)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        try {
                            val width = size.width
                            val height = size.height

                            // Dynamic or clamped ranges for independent axes
                            val hrValues = points.mapNotNull { if (it.adjustedBpm.isNaN()) null else it.adjustedBpm }
                            val tempValues = points.mapNotNull { if (it.temperatureCelsius.isNaN()) null else it.temperatureCelsius }

                            val hrMin = if (hrValues.isNotEmpty()) min(40f, hrValues.minOrNull() ?: 40f) else 40f
                            val hrMax = if (hrValues.isNotEmpty()) max(130f, hrValues.maxOrNull() ?: 130f) else 130f
                            val hrRange = if (hrMax - hrMin <= 0f) 1f else (hrMax - hrMin)

                            val tempMin = if (tempValues.isNotEmpty()) min(20f, tempValues.minOrNull() ?: 20f) else 20f
                            val tempMax = if (tempValues.isNotEmpty()) max(42f, tempValues.maxOrNull() ?: 42f) else 42f
                            val tempRange = if (tempMax - tempMin <= 0f) 1f else (tempMax - tempMin)

                            // Horizontal background grid lines (3 lines)
                            val gridLineCount = 3
                            for (i in 0..gridLineCount) {
                                val y = height * (i.toFloat() / gridLineCount)
                                drawLine(
                                    color = NavyCardBorder.copy(alpha = 0.5f),
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            }

                            if (points.size == 1) {
                                // Draw single point dots
                                val pt = points[0]
                                if (!pt.adjustedBpm.isNaN()) {
                                    val normalizedY = 1f - ((pt.adjustedBpm - hrMin) / hrRange)
                                    val y = (normalizedY * (height - 24f)) + 12f
                                    drawCircle(
                                        color = HeartRateRed,
                                        radius = 5.dp.toPx(),
                                        center = Offset(width / 2f, y)
                                    )
                                }
                                if (!pt.temperatureCelsius.isNaN()) {
                                    val normalizedY = 1f - ((pt.temperatureCelsius - tempMin) / tempRange)
                                    val y = (normalizedY * (height - 24f)) + 12f
                                    drawCircle(
                                        color = TempCyan,
                                        radius = 5.dp.toPx(),
                                        center = Offset(width / 2f, y)
                                    )
                                }
                            } else {
                                val stepX = width / (points.size - 1)

                                // Build Heart Rate Path (Left Axis Scale, Red Line)
                                val hrPath = Path()
                                var hrStarted = false
                                for (i in points.indices) {
                                    val valHr = points[i].adjustedBpm
                                    if (!valHr.isNaN()) {
                                        val x = i * stepX
                                        val normalizedY = 1f - ((valHr - hrMin) / hrRange)
                                        val y = (normalizedY * (height - 24f)) + 12f
                                        if (!hrStarted) {
                                            hrPath.moveTo(x, y)
                                            hrStarted = true
                                        } else {
                                            hrPath.lineTo(x, y)
                                        }
                                    }
                                }

                                if (hrStarted) {
                                    drawPath(
                                        path = hrPath,
                                        color = HeartRateRed,
                                        style = Stroke(
                                            width = 3.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                // Build Temperature Path (Right Axis Scale, Cyan Line)
                                val tempPath = Path()
                                var tempStarted = false
                                for (i in points.indices) {
                                    val valTemp = points[i].temperatureCelsius
                                    if (!valTemp.isNaN()) {
                                        val x = i * stepX
                                        val normalizedY = 1f - ((valTemp - tempMin) / tempRange)
                                        val y = (normalizedY * (height - 24f)) + 12f
                                        if (!tempStarted) {
                                            tempPath.moveTo(x, y)
                                            tempStarted = true
                                        } else {
                                            tempPath.lineTo(x, y)
                                        }
                                    }
                                }

                                if (tempStarted) {
                                    drawPath(
                                        path = tempPath,
                                        color = TempCyan,
                                        style = Stroke(
                                            width = 3.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Catch any unexpected drawing calculation issues safely
                        }
                    }
                }
            }

            // Bottom axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Earlier",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted
                )
                Text(
                    text = "Dual Independent Y-Axis Telemetry",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted
                )
                Text(
                    text = "Live Now",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted
                )
            }
        }
    }
}
