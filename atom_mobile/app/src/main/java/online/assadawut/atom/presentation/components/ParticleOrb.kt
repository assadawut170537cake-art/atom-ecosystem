package online.assadawut.atom.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

enum class AssistantState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@Composable
fun ParticleOrb(
    state: AssistantState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")
    
    // Scale animation depends on the state
    val scaleMultiplier = when (state) {
        AssistantState.IDLE -> 1.0f
        AssistantState.LISTENING -> 1.2f
        AssistantState.THINKING -> 0.9f
        AssistantState.SPEAKING -> 1.3f
    }
    
    val duration = when (state) {
        AssistantState.IDLE -> 2000
        AssistantState.LISTENING -> 1000
        AssistantState.THINKING -> 500
        AssistantState.SPEAKING -> 300
    }
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f * scaleMultiplier,
        targetValue = 1.0f * scaleMultiplier,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    Canvas(modifier = modifier.size(200.dp)) {
        scale(scale) {
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = size.minDimension / 2
            )
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = size.minDimension / 2.5f
            )
            drawCircle(
                color = color,
                radius = size.minDimension / 3
            )
        }
    }
}
