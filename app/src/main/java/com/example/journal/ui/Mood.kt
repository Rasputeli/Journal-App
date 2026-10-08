package com.example.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.journal.data.MoodScale

/**
 * Five numbered levels plus one free-form slot. Picking a numbered level clears
 * any custom emoji and the other way round, so a block carries one mood.
 */
@Composable
fun MoodChipRow(
    scale: MoodScale,
    valence: Int?,
    customMood: String?,
    onValence: (Int?) -> Unit,
    onCustom: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(scale.levels.size) { index ->
            val level = scale.levels[index]
            val value = index - MoodScale.OFFSET
            FilterChip(
                selected = valence == value,
                onClick = { onValence(if (valence == value) null else value) },
                label = { Text(level.emoji, fontSize = 18.sp) },
            )
        }
        item {
            FilterChip(
                selected = customMood != null,
                onClick = onCustom,
                label = { Text(customMood ?: "+", fontSize = 18.sp) },
            )
        }
    }
}

@Composable
fun NotebookChipRow(
    notebooks: List<com.example.journal.data.Notebook>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(notebooks.size) { index ->
            val notebook = notebooks[index]
            FilterChip(
                selected = selectedId == notebook.id,
                onClick = { onSelect(notebook.id) },
                label = { Text(notebook.name) },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onCreate,
                label = { Text("New") },
            )
        }
    }
}
