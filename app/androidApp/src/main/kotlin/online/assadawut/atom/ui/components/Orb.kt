package online.assadawut.atom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.assadawut.atom.core.model.OrbState

@Composable
fun Orb(state: OrbState, modifier: Modifier = Modifier) {
    val color = when (state) {
        OrbState.IDLE -> Color.Cyan
        OrbState.LISTENING -> Color.Green
        OrbState.THINKING -> Color.Magenta
        OrbState.SPEAKING -> Color.Yellow
    }
    Box(
        modifier = modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(color)
    )
}