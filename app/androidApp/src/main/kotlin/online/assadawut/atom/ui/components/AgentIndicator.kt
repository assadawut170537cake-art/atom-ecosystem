package online.assadawut.atom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.assadawut.atom.core.model.AgentPersona
import online.assadawut.atom.ui.theme.AtomCyan
import online.assadawut.atom.ui.theme.FridayOrange
import online.assadawut.atom.ui.theme.JevBlue
import online.assadawut.atom.ui.theme.JulesGreen
import online.assadawut.atom.ui.theme.UltronRed

@Composable
fun AgentIndicator(agent: AgentPersona, modifier: Modifier = Modifier) {
    val color = when (agent) {
        AgentPersona.ATOM -> AtomCyan
        AgentPersona.FRIDAY -> FridayOrange
        AgentPersona.ULTRON -> UltronRed
        AgentPersona.JEV -> JevBlue
        AgentPersona.JULES -> JulesGreen
    }
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = agent.name, color = Color.Black)
    }
}