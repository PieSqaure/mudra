package com.pisquarelabs.mudra.ui.caption

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pisquarelabs.mudra.ui.camera.CameraScreen

@Composable
fun CaptionScreen(
    viewModel: CaptionViewModel,
    onOpenSettings: () -> Unit,
    createAnalyzer: (onResult: (List<com.pisquarelabs.mudra.core.model.HandFrame>, Int, Int) -> Unit) -> com.pisquarelabs.mudra.core.vision.HandLandmarkerAnalyzer
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        CameraScreen(
            createAnalyzer = createAnalyzer,
            onHandFrames = viewModel::onHandFrames
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                usingOnDeviceQwen = state.usingOnDeviceQwen,
                onOpenSettings = onOpenSettings
            )
            Spacer(modifier = Modifier.weight(1f))
            LiveCandidateChip(state)
            CaptionCard(state, onConfirm = viewModel::confirmResolved, onDismiss = viewModel::dismissResolved)
        }
    }
}

@Composable
private fun TopBar(usingOnDeviceQwen: Boolean, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (usingOnDeviceQwen) "Mudra · Qwen (on-device)" else "Mudra · Demo reasoner",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
private fun LiveCandidateChip(state: CaptionUiState) {
    val candidate = state.liveCandidate
    if (!state.handDetected || candidate == null) return

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Text(
                text = "${candidate.gloss}  ${(candidate.confidence * 100).toInt()}%",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CaptionCard(
    state: CaptionUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.isReasoning) {
                Text("Reconstructing...", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }

            val resolved = state.resolved
            if (resolved == null) {
                Text(
                    "Sign to Mudra - it will reconstruct short phrases here.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Text(resolved.text.ifBlank { "(no clear signs detected)" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "intent: ${resolved.intent} · confidence: ${(resolved.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (resolved.needsConfirmation) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Discard") }
                    TextButton(onClick = onConfirm) { Text("Confirm & speak") }
                }
            }
        }
    }
}
