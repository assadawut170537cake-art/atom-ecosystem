package online.assadawut.atom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QueueScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Task Queue", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            // TODO: Queue list items
        }
    }
}