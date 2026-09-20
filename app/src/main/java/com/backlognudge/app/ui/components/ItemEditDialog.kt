package com.backlognudge.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.EnergyLevel
import com.backlognudge.app.data.ItemCategory
import com.backlognudge.app.data.TimeEstimate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditDialog(
    initial: BacklogItem?,
    onDismiss: () -> Unit,
    onSave: (BacklogItem) -> Unit,
    // Only offered when editing an existing item. Deleting lives here rather than
    // as a per-row button: a trash can beside every row invites misses.
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var minutes by remember { mutableStateOf(initial?.estimatedMinutes ?: TimeEstimate.MIN_15) }
    var energy by remember { mutableStateOf(initial?.energy ?: EnergyLevel.MEDIUM) }
    var category by remember { mutableStateOf(initial?.category ?: ItemCategory.OTHER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // The poster face is never set in sentence case.
        title = { Text(if (initial == null) "ADD BACKLOG ITEM" else "EDIT ITEM") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What is it?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Text("Time estimate", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeEstimate.values().forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Energy", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnergyLevel.values().forEach { option ->
                        FilterChip(
                            selected = energy == option,
                            onClick = { energy = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemCategory.values().forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val result = (initial ?: BacklogItem(title = title)).copy(
                        title = title.trim(),
                        estimatedMinutes = minutes,
                        energy = energy,
                        category = category,
                        needsReview = false
                    )
                    onSave(result)
                }
            ) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Backing out isn't "going", so it doesn't get the accent.
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}
