package hu.konyvtar.tts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.tts.TtsService
import hu.konyvtar.tts.ui.ExplorerScreen
import hu.konyvtar.tts.ui.FilePickerScreen
import hu.konyvtar.tts.ui.ReaderScreen
import hu.konyvtar.tts.ui.ShelfScreen
import hu.konyvtar.tts.ui.SettingsScreen
import hu.konyvtar.tts.ui.StatsScreen
import hu.konyvtar.tts.ui.theme.KonyvtarTheme
import hu.konyvtar.tts.ui.theme.ThemeState
import hu.konyvtar.tts.vm.LibraryViewModel
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(hu.konyvtar.tts.data.LocaleHelper.wrap(newBase))
    }

    private var openPlayerOnStart = false

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerOnStart = intent?.getBooleanExtra("open_player", false) == true
        ThemeState.load(this)

        // Értesítési engedély (Android 13+), hogy a felolvasó vezérlősáv látsszon
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            KonyvtarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(startInPlayer = openPlayerOnStart)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun AppRoot(startInPlayer: Boolean) {
    var hasAllFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    // Visszatéréskor (a beállításokból) frissítjük az engedély állapotát
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFiles = Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasAllFiles) {
        PermissionScreen()
        return
    }

    val nav = rememberNavController()
    val vm: LibraryViewModel = viewModel()

    fun openReaderFor(row: FileRow) {
        vm.readerTarget = LibraryViewModel.ReaderTarget(
            path = row.path,
            title = row.cim ?: row.name.substringBeforeLast('.'),
            author = row.szerzo ?: ""
        )
        nav.navigate("reader")
    }

    /** Az éppen felolvasott könyv megnyitása az egyesített olvasó képernyőn. */
    fun openNowPlayingReader() {
        val s = TtsService.state.value
        val p = s.path ?: return
        vm.readerTarget = LibraryViewModel.ReaderTarget(p, s.title, s.author)
        nav.navigate("reader")
    }

    // Értesítésre koppintva egyből a most szóló könyv olvasója nyílik
    if (startInPlayer) {
        LaunchedEffect(Unit) { openNowPlayingReader() }
    }

    NavHost(
        navController = nav,
        startDestination = "shelf"
    ) {
        composable("shelf") {
            ShelfScreen(
                vm = vm,
                onOpenBook = { row -> openReaderFor(row) },
                onOpenFiles = { nav.navigate("explorer") },
                onOpenStats = { nav.navigate("stats") },
                onOpenSettings = { nav.navigate("settings") },
                onPickRoot = { nav.navigate("pick_root") }
            )
        }
        composable("explorer") {
            ExplorerScreen(
                vm = vm,
                onOpenPlayer = { openNowPlayingReader() },
                onOpenStats = { nav.navigate("stats") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenReader = { row -> openReaderFor(row) }
            )
        }
        composable("reader") {
            val t = vm.readerTarget
            if (t == null) {
                nav.popBackStack()
            } else {
                ReaderScreen(
                    path = t.path,
                    title = t.title,
                    author = t.author,
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
        }
        composable("stats") {
            StatsScreen(
                onBack = { nav.popBackStack() },
                onOpenReader = { p ->
                    vm.readerTarget = LibraryViewModel.ReaderTarget(p.path, p.title, p.author)
                    nav.navigate("reader")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onPickRoot = { nav.navigate("pick_root") }
            )
        }
        composable("pick_root") {
            FilePickerScreen(
                title = stringResource(R.string.picker_root_title),
                pickDirectory = true,
                onPicked = { path ->
                    vm.setRoot(path)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() }
            )
        }
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.perm_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.perm_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:" + context.packageName)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
        }) {
            Text(stringResource(R.string.perm_button))
        }
    }
}
