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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.ui.MainNav
import hu.konyvtar.tts.ui.MainView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import hu.konyvtar.tts.ui.LibraryScreen
import hu.konyvtar.tts.ui.ReaderScreen
import hu.konyvtar.tts.ui.ShelfScreen
import hu.konyvtar.tts.ui.SettingsScreen
import hu.konyvtar.tts.ui.StatsScreen
import hu.konyvtar.tts.ui.theme.KonyvtarTheme
import hu.konyvtar.tts.ui.theme.ThemeState
import hu.konyvtar.tts.vm.BrowserViewModel
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
    val browser: BrowserViewModel = viewModel()
    val rootScope = rememberCoroutineScope()
    val player by TtsService.state.collectAsState()

    // A könyvtár és a fájlböngésző lapozója. Azért itt születik és nem a
    // "main" útvonalon belül, mert az alsó sáv gombjainak is lapoznia kell
    // vele — akkor is, ha épp egy másik képernyőről térünk vissza.
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Az olvasó gomb akkor is éljen, ha épp nem szól semmi: ilyenkor a
    // legutóbb hallgatott könyvre ugrik. Enélkül a sáv harmada halott lenne
    // közvetlenül indítás után.
    var lastRead by remember { mutableStateOf<LibraryViewModel.ReaderTarget?>(null) }
    LaunchedEffect(player.path) {
        lastRead = withContext(Dispatchers.IO) {
            AppDb.lastListened()?.let {
                LibraryViewModel.ReaderTarget(it.path, it.title, it.author)
            }
        }
    }

    fun openReaderFor(row: FileRow) {
        vm.readerTarget = LibraryViewModel.ReaderTarget(
            path = row.path,
            title = row.cim ?: row.name.substringBeforeLast('.'),
            author = row.szerzo ?: ""
        )
        nav.navigate("reader")
    }

    /**
     * Az olvasó képernyő megnyitása: ha szól valami, arra; ha nem, a legutóbb
     * hallgatott könyvre. Ha már ott vagyunk, nem teszünk rá még egy másolatot
     * a visszalépési veremre.
     */
    fun openReader() {
        val s = TtsService.state.value
        val target = s.path?.let { LibraryViewModel.ReaderTarget(it, s.title, s.author) }
            ?: lastRead
            ?: return
        vm.readerTarget = target
        if (nav.currentDestination?.route != "reader") nav.navigate("reader")
    }

    /** Vissza a lapozható főképernyőre, a megadott lapra. */
    fun goToPage(page: Int) {
        if (nav.currentDestination?.route != "main") nav.popBackStack("main", false)
        rootScope.launch { pagerState.animateScrollToPage(page) }
    }

    /** Az alsó sáv adatai az adott nézethez. */
    fun navFor(current: MainView) = MainNav(
        current = current,
        readerEnabled = player.path != null || lastRead != null,
        onLibrary = { goToPage(0) },
        onFiles = { goToPage(1) },
        onReader = { openReader() }
    )

    // Értesítésre koppintva egyből a most szóló könyv olvasója nyílik
    if (startInPlayer) {
        LaunchedEffect(Unit) { openReader() }
    }

    NavHost(
        navController = nav,
        startDestination = "main"
    ) {
        // A könyvtár és a fájlböngésző egyetlen lapozható felület: jobbra-balra
        // pöccintve váltasz köztük. A polc külön marad, mert ott a lapozás már
        // a könyvek közti mozgást jelenti.
        composable("main") {
            // A második lapon a rendszer-vissza az első lapra visz, nem kilép
            BackHandler(enabled = pagerState.currentPage != 0) {
                rootScope.launch { pagerState.animateScrollToPage(0) }
            }

            // Lapozás közben mindkét lap látszik egy pillanatra; hogy az alsó
            // sáv ne villogjon, mindkettő ugyanazt a kijelölést kapja.
            val pagerView =
                if (pagerState.currentPage == 0) MainView.LIBRARY else MainView.FILES

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    LibraryScreen(
                        vm = vm,
                        mainNav = navFor(pagerView),
                        onOpenBook = { row -> openReaderFor(row) },
                        onOpenShelf = { nav.navigate("shelf") },
                        onOpenFiles = { goToPage(1) },
                        onOpenStats = { nav.navigate("stats") },
                        onOpenSettings = { nav.navigate("settings") },
                        onPickRoot = { nav.navigate("pick_root") },
                        pageIndex = pagerState.currentPage,
                        pageCount = 2
                    )
                } else {
                    ExplorerScreen(
                        vm = vm,
                        browser = browser,
                        mainNav = navFor(pagerView),
                        onOpenStats = { nav.navigate("stats") },
                        onOpenSettings = { nav.navigate("settings") },
                        onOpenReader = { row -> openReaderFor(row) },
                        onOpenLibrary = { goToPage(0) },
                        pageIndex = pagerState.currentPage,
                        pageCount = 2
                    )
                }
            }
        }
        composable("shelf") {
            ShelfScreen(
                vm = vm,
                mainNav = navFor(MainView.OTHER),
                onOpenBook = { row -> openReaderFor(row) },
                onBack = { nav.popBackStack() }
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
                    mainNav = navFor(MainView.READER),
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
        }
        composable("stats") {
            StatsScreen(
                mainNav = navFor(MainView.OTHER),
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
                mainNav = navFor(MainView.OTHER),
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
                    browser.navigateTo(path)
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
