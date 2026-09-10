package com.pisquarelabs.mudra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pisquarelabs.mudra.ui.navigation.MudraNavHost
import com.pisquarelabs.mudra.ui.theme.MudraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as MudraApplication).container

        setContent {
            MudraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var hasCameraPermission by remember { mutableStateOf(hasCameraPermission()) }
                    val requestPermission = rememberPermissionLauncher { hasCameraPermission = it }

                    if (hasCameraPermission) {
                        MudraNavHost(container = container)
                    } else {
                        CameraPermissionRationale(onRequestPermission = { requestPermission() })
                    }
                }
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun rememberPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> onResult(granted) }
        return { launcher.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
private fun CameraPermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Mudra needs camera access to see hand movements and translate them into speech and captions.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Grant camera access")
        }
    }
}
