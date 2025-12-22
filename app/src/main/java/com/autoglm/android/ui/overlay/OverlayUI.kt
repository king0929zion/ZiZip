package com.autoglm.android.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.PrimaryBlack
import com.autoglm.android.ui.theme.PrimaryWhite
import com.autoglm.android.ui.theme.ZiZipTypography

@Composable
fun OverlayContent(
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    if (isExpanded) {
        TaskPanel(onCollapse = onToggle)
    } else {
        FloatingBall(onClick = onToggle)
    }
}

@Composable
fun FloatingBall(
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(64.dp) // Adjusted size
            .pointerInput(Unit) {
               detectDragGestures { change, dragAmount ->
                   change.consume()
                   // Callback to move window would go here
               }
            }
            .pointerInput(Unit) { 
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Ring / "The Eye"
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }) {
            // Drop Shadow equivalent (Blurred circle behind)
            drawCircle(
                color = Color.Black.copy(alpha = 0.2f),
                radius = size.minDimension / 2,
                center = center
            )
            
            // Main Black Circle Background
            drawCircle(
                color = PrimaryBlack,
                radius = (size.minDimension / 2) * 0.9f,
                center = center
            )
            
            // Inner "Pupil" styled ring
            drawCircle(
                color = PrimaryWhite,
                radius = (size.minDimension / 2) * 0.35f,
                style = Stroke(width = 4.dp.toPx())
            )
            
            // Core Dot
             drawCircle(
                color = PrimaryWhite,
                radius = (size.minDimension / 2) * 0.15f,
            )
        }
    }
}

@Composable
fun TaskPanel(onCollapse: () -> Unit) {
    Box(
        modifier = Modifier
            .size(300.dp, 400.dp)
            .background(PrimaryWhite, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        androidx.compose.foundation.layout.Column {
            Text("ZiZip Active", style = ZiZipTypography.headlineMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text("Processing screen content...", style = ZiZipTypography.bodyMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            androidx.compose.material3.Button(onClick = onCollapse) {
                Text("Minimize")
            }
        }
    }
}
