package io.github.vieenrose.liveradiokaraoke

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.media3.common.util.UnstableApi
import io.github.vieenrose.liveradiokaraoke.ui.MainScreen
import io.github.vieenrose.liveradiokaraoke.ui.theme.KaraokeTheme
import io.github.vieenrose.liveradiokaraoke.vm.KaraokeViewModel

@UnstableApi
class MainActivity : ComponentActivity() {

    private val vm: KaraokeViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { KaraokeTheme { MainScreen(vm) } }
    }
}
