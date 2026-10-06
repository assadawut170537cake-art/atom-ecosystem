package online.assadawut.atom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import online.assadawut.atom.core.model.OrbState
import online.assadawut.atom.ui.components.Orb
import online.assadawut.atom.ui.components.TopBar

@Composable
fun MainScreen() {
    Scaffold(
        topBar = { TopBar(onSettingsClick = {}) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Orb(state = OrbState.IDLE)
        }
    }
}