package com.pisquarelabs.mudra.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pisquarelabs.mudra.data.settings.MudraSettings
import com.pisquarelabs.mudra.data.settings.QwenModelVariant
import com.pisquarelabs.mudra.data.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val settings by settingsRepository.settings.collectAsState(initial = MudraSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SectionTitle("Language reasoning")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Use on-device Qwen")
                        Text(
                            "Off: deterministic demo reconstruction. On: requires a Qwen GGUF model + llama.cpp bridge (see native/README.md); falls back automatically if unavailable.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.useOnDeviceQwen,
                        onCheckedChange = { scope.launch { settingsRepository.setUseOnDeviceQwen(it) } }
                    )
                }
            }
            item {
                Text("Qwen variant", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            items(QwenModelVariant.entries) { variant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${variant.label} (${variant.paramCount} params)")
                    Switch(
                        checked = settings.qwenModelVariant == variant,
                        onCheckedChange = { if (it) scope.launch { settingsRepository.setQwenVariant(variant) } }
                    )
                }
            }

            item { Divider() }
            item { SectionTitle("Sign detection") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Minimum confidence: ${(settings.minSignConfidence * 100).toInt()}%")
                    Slider(
                        value = settings.minSignConfidence,
                        onValueChange = { scope.launch { settingsRepository.setMinSignConfidence(it) } },
                        valueRange = 0.3f..0.9f
                    )
                }
            }

            item { Divider() }
            item { SectionTitle("Output") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Speak resolved phrases (TTS)")
                    Switch(
                        checked = settings.ttsEnabled,
                        onCheckedChange = { scope.launch { settingsRepository.setTtsEnabled(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
