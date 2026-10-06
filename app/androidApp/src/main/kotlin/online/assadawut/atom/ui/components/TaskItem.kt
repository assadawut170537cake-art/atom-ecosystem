package online.assadawut.atom.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.assadawut.atom.database.QueueRecord

@Composable
fun TaskItem(task: QueueRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Agent: ${task.agent}", style = MaterialTheme.typography.titleMedium)
            Text("Type: ${task.taskType}")
            Text("Status: ${task.status}")
        }
    }
}