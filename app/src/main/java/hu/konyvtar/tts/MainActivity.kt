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
import hu.konyvtar.tts.ui.DetailScreen
import hu.konyvtar.tts.ui.ExplorerScreen
import hu.konyvtar.tts.ui.FilePickerScreen
import hu.konyvtar.tts.ui.ReaderScreen
import hu.konyvtar.tts.ui.SettingsScreen
import hu.konyvtar.tts.ui.StatsScreen
import hu.konyvtar.tts.ui.theme.KonyvtarTheme
import hu.konyvtar.tts.vm.LibraryViewModel

class MainActivity : ComponentActivity() {

    private var openPlayerOnStart = false

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerOnStart = intent?.getBooleanExtra("open_player", false) == true

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
        startDestination = "explorer"
    ) {
        composable("explorer") {
            ExplorerScreen(
                vm = vm,
                onOpenDetail = { nav.navigate("detail") },
                onOpenPlayer = { openNowPlayingReader() },
                onOpenStats = { nav.navigate("stats") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenReader = { row -> openReaderFor(row) }
            )
        }
        composable("detail") {
            val row = vm.selectedFile
            if (row == null) {
                nav.popBackStack()
            } else {
                DetailScreen(
                    row = row,
                    onBack = { nav.popBackStack() },
                    onOpenReader = { openReaderFor(row) }
                )
            }
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
                    onBack = { nav.popBackStack() }
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
                onPickDb = { nav.navigate("pick_db") },
                onPickRoot = { nav.navigate("pick_root") }
            )
        }
        composable("pick_db") {
            FilePickerScreen(
                title = "Adatbázis kiválasztása",
                pickDirectory = false,
                extensionFilter = setOf("db", "sqlite", "sqlite3"),
                onPicked = { path ->
                    vm.openDb(path)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("pick_root") {
            FilePickerScreen(
                title = "Gyökérmappa kiválasztása",
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
            text = "Fájlhozzáférés szükséges",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Az app a telefonon lévő könyvfájlokat és a katalógus-adatbázist olvassa, " +
                "ezért a „Minden fájl kezelése” engedélyre van szüksége.\n\n" +
                "A következő képernyőn kapcsold be az engedélyt, majd lépj vissza.",
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
            Text("Engedély megadása")
        }
    }
}
