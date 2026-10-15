package com.example.journal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SectionPhotos(
    names: List<String>,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    fullImageFor: suspend (String) -> ImageBitmap?,
) {
    if (names.isEmpty()) return
    var viewing by remember { mutableStateOf<String?>(null) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        items(names.size) { index ->
            val name = names[index]
            Thumbnail(
                name = name,
                thumbnailFor = thumbnailFor,
                onClick = { viewing = name },
            )
        }
    }

    viewing?.let { name ->
        PhotoViewer(
            name = name,
            fullImageFor = fullImageFor,
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun Thumbnail(
    name: String,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, name) {
        value = thumbnailFor(name)
    }

    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val picture = bitmap
        if (picture == null) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PhotoViewer(
    name: String,
    fullImageFor: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, name) {
        value = fullImageFor(name)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val picture = bitmap
                if (picture == null) {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                } else {
                    Image(
                        bitmap = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "Tap outside to close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
