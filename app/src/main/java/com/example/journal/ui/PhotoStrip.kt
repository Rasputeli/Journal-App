package com.example.journal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun PhotoStrip(
    names: List<String>,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    fullImageFor: suspend (String) -> ImageBitmap?,
    onRemove: (String) -> Unit,
    onAddFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    var viewing by remember { mutableStateOf<String?>(null) }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            AssistChip(onClick = onTakePhoto, label = { Text("Camera") })
        }
        item {
            AssistChip(onClick = onAddFromGallery, label = { Text("Gallery") })
        }
        items(names.size) { index ->
            val name = names[index]
            Box {
                AttachmentThumbnail(
                    name = name,
                    thumbnailFor = thumbnailFor,
                    onClick = { viewing = name },
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clickable { onRemove(name) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("x", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    viewing?.let { name ->
        PhotoViewer(name = name, fullImageFor = fullImageFor, onDismiss = { viewing = null })
    }
}

@Composable
private fun AttachmentThumbnail(
    name: String,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, name) {
        value = thumbnailFor(name)
    }

    Box(
        modifier = Modifier
            .size(76.dp)
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
                modifier = Modifier.fillMaxSize(),
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
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                    "Tap outside to close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
