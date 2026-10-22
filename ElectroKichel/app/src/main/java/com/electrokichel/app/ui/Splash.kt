package com.electrokichel.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.electrokichel.app.R
import kotlinx.coroutines.delay

/** Splash 3D: el logo gira 360° sobre su eje con perspectiva, rebote de escala y halo pulsante. */
@Composable
fun Splash(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true; delay(2600); onDone() }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }

    val inf = rememberInfiniteTransition(label = "spin")
    val rotY by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "rotY"
    )
    val glow by inf.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )
    val float by inf.animateFloat(
        -10f, 10f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(EkBg2, EkBg), center = Offset.Unspecified, radius = 1400f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Halo
                Image(
                    painter = painterResource(R.drawable.logo_electrokichel),
                    contentDescription = null,
                    modifier = Modifier
                        .size(230.dp)
                        .scale(1.18f)
                        .blur(34.dp)
                        .graphicsLayer { alpha = glow * 0.55f * intro.value }
                )
                // Logo girando en 3D
                Image(
                    painter = painterResource(R.drawable.logo_electrokichel),
                    contentDescription = "ElectroKichel",
                    modifier = Modifier
                        .size(230.dp)
                        .offset(y = float.dp)
                        .graphicsLayer {
                            rotationY = rotY
                            cameraDistance = 14f * density
                            val s = 0.4f + 0.6f * intro.value
                            scaleX = s; scaleY = s
                            alpha = intro.value
                        }
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "ELECTROKICHEL",
                color = EkText, fontSize = 26.sp, fontWeight = FontWeight.Black,
                letterSpacing = 4.sp, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = intro.value }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Soluciones en tecnología",
                color = EkDim, fontSize = 13.sp, letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer { alpha = glow * intro.value }
            )
        }
    }
}
