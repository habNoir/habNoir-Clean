package xyz.habnoir.clean

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.habnoir.clean.ui.icons.HabNoirIcons
import xyz.habnoir.clean.ui.screen.AppsScreen
import xyz.habnoir.clean.ui.screen.HomeScreen
import xyz.habnoir.clean.ui.screen.OnboardingScreen
import xyz.habnoir.clean.ui.screen.SettingsScreen
import xyz.habnoir.clean.ui.screen.TrashScreen
import xyz.habnoir.clean.ui.theme.HabNoirColors
import xyz.habnoir.clean.ui.theme.HabNoirTheme
import xyz.habnoir.clean.ui.viewmodel.MainEffect
import xyz.habnoir.clean.ui.viewmodel.MainEvent
import xyz.habnoir.clean.ui.viewmodel.MainTab
import xyz.habnoir.clean.ui.viewmodel.MainViewModel
import xyz.habnoir.clean.ui.viewmodel.MainUiState

class MainActivity : ComponentActivity() {

    private var askedNotificationPermission = false

    private val viewModel: MainViewModel by viewModels {
        val app = application as HabNoirApp
        MainViewModel.Factory(applicationContext, app.container)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ASKED_NOTIF, askedNotificationPermission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedNotificationPermission = savedInstanceState?.getBoolean(KEY_ASKED_NOTIF) == true
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        runCatching {
            val bestRate = display?.supportedModes.orEmpty()
                .map { it.refreshRate }
                .filter { it <= 121f }
                .maxOrNull()
            if (bestRate != null && bestRate >= 90f) {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = bestRate
                }
            }
        }

        val container = (application as HabNoirApp).container

        setContent {
            HabNoirTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val currentResources by rememberUpdatedState(LocalResources.current)
                var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.onEvent(MainEvent.RefreshPermissions)
                }
                val currentNotificationLauncher by rememberUpdatedState(notificationLauncher)

                val dispatchEvent: (MainEvent) -> Unit = remember(viewModel) { viewModel::onEvent }

                val requestNotificationPermission: () -> Unit = remember(viewModel) {
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            when {
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED -> {
                                    viewModel.onEvent(MainEvent.RefreshPermissions)
                                }
                                askedNotificationPermission &&
                                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                                    runCatching {
                                        startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        )
                                    }
                                    Unit
                                }
                                else -> {
                                    askedNotificationPermission = true
                                    currentNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        } else {
                            viewModel.onEvent(MainEvent.RefreshPermissions)
                        }
                    }
                }

                val trashDeleteLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) {
                    viewModel.onEvent(MainEvent.OnLayer1ChunkResult)
                }

                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is MainEffect.ShowSnackbar -> {
                                val msg = if (effect.args.isEmpty()) {
                                    currentResources.getString(effect.messageRes)
                                } else {
                                    currentResources.getString(effect.messageRes, *effect.args.toTypedArray())
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                            is MainEffect.LaunchTrashDeleteSender -> {
                                val req = IntentSenderRequest.Builder(effect.pendingIntent.intentSender).build()
                                trashDeleteLauncher.launch(req)
                            }
                            is MainEffect.OpenSettings -> {
                                isSettingsOpen = true
                            }
                        }
                    }
                }

                LifecycleResumeEffect(Unit) {
                    viewModel.onEvent(MainEvent.RefreshPermissions)
                    onPauseOrDispose { }
                }

                BackHandler(enabled = state.showOnboarding || isSettingsOpen) {
                    if (state.showOnboarding) {
                        viewModel.onEvent(MainEvent.CompleteOnboarding)
                    } else {
                        isSettingsOpen = false
                    }
                }

                val openSettings = remember { { isSettingsOpen = true } }
                val closeSettings = remember { { isSettingsOpen = false } }

                if (state.showOnboarding) {
                    OnboardingScreen(
                        state = state,
                        onEvent = dispatchEvent,
                        onRequestNotificationPermission = requestNotificationPermission
                    )
                } else {
                    HabNoirAppShell(
                        state = state,
                        iconCache = container.iconMemoryCache,
                        snackbarHostState = snackbarHostState,
                        isSettingsOpen = isSettingsOpen,
                        onOpenSettings = openSettings,
                        onCloseSettings = closeSettings,
                        onEvent = dispatchEvent
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_ASKED_NOTIF = "asked_notification_permission"
    }
}

@Stable
private class TabDeckState(
    initialTab: MainTab,
    private val scope: CoroutineScope,
    private val onTabPersisted: (Int) -> Unit
) {
    var activeTab by mutableStateOf(initialTab)
        private set
    var exitingTab by mutableStateOf<MainTab?>(null)
        private set
    var direction by mutableFloatStateOf(1f)
        private set
    var animReady by mutableStateOf(true)
        private set
    val progress = Animatable(1f)

    private var animJob: Job? = null

    fun effectiveProgress(): Float = if (exitingTab != null && !animReady) 0f else progress.value

    fun select(target: MainTab) {
        if (target == activeTab) return
        direction = if (target.ordinal > activeTab.ordinal) 1f else -1f
        exitingTab = activeTab
        activeTab = target
        animReady = false
        onTabPersisted(target.ordinal)

        animJob?.cancel()
        animJob = scope.launch {
            progress.snapTo(0f)
            animReady = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 190,
                    easing = TabTransitionEasing
                )
            )
            exitingTab = null
        }
    }

    companion object {
        private val TabTransitionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
}

@Composable
private fun HabNoirAppShell(
    state: MainUiState,
    iconCache: xyz.habnoir.clean.engine.scanner.AppIconMemoryCache,
    snackbarHostState: SnackbarHostState,
    isSettingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onEvent: (MainEvent) -> Unit
) {
    val density = LocalDensity.current
    val windowSizePx = LocalWindowInfo.current.containerSize
    val maxWidth = with(density) { windowSizePx.width.toDp() }
    val useRail = maxWidth >= 600.dp
    val isExpanded = maxWidth >= 840.dp
    val isCompactNarrow = maxWidth < 360.dp

    val homeListState = rememberLazyListState()
    val appsListState = rememberLazyListState()
    val trashScrollState = rememberScrollState()

    var savedTabOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val deckScope = rememberCoroutineScope()
    val deckState = remember(deckScope) {
        TabDeckState(
            initialTab = MainTab.entries.getOrElse(savedTabOrdinal) { MainTab.SPACE },
            scope = deckScope,
            onTabPersisted = { savedTabOrdinal = it }
        )
    }
    var mountedMask by remember { mutableIntStateOf(1 shl deckState.activeTab.ordinal) }
    LaunchedEffect(Unit) {
        delay(180)
        mountedMask = 0b111
    }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(isSettingsOpen) {
        if (isSettingsOpen) {
            focusManager.clearFocus()
        }
    }

    val selectTab = remember(deckState, focusManager) {
        { tab: MainTab ->
            val bit = 1 shl tab.ordinal
            if ((mountedMask and bit) == 0) {
                mountedMask = mountedMask or bit
            }
            if (tab != MainTab.APPS) {
                focusManager.clearFocus()
            }
            deckState.select(tab)
        }
    }
    val openAppsTab = remember(selectTab) { { selectTab(MainTab.APPS) } }
    val handleOpenSettings = remember(focusManager, onOpenSettings) {
        {
            focusManager.clearFocus()
            onOpenSettings()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HabNoirColors.ObsidianVoid)
    ) {
        if (useRail) {
            Scaffold(
                containerColor = HabNoirColors.ObsidianVoid,
                topBar = {
                    HabNoirTopBar(
                        deviceLabel = state.deviceIdentity.headerLabel,
                        compactNarrow = false,
                        onOpenSettings = handleOpenSettings
                    )
                },
                snackbarHost = { HabNoirSnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {
                    HabNoirNavRail(
                        deckState = deckState,
                        onSelectTab = selectTab
                    )
                    TabDeckHost(
                        deckState = deckState,
                        mountedMask = mountedMask,
                        state = state,
                        iconCache = iconCache,
                        isExpanded = isExpanded,
                        isCompactNarrow = false,
                        homeListState = homeListState,
                        appsListState = appsListState,
                        trashScrollState = trashScrollState,
                        onOpenApps = openAppsTab,
                        onOpenSettings = handleOpenSettings,
                        onEvent = onEvent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = HabNoirColors.ObsidianVoid,
                topBar = {
                    HabNoirTopBar(
                        deviceLabel = state.deviceIdentity.headerLabel,
                        compactNarrow = isCompactNarrow,
                        onOpenSettings = handleOpenSettings
                    )
                },
                bottomBar = {
                    HabNoirBottomBar(
                        deckState = deckState,
                        onSelectTab = selectTab
                    )
                },
                snackbarHost = { HabNoirSnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                TabDeckHost(
                    deckState = deckState,
                    mountedMask = mountedMask,
                    state = state,
                    iconCache = iconCache,
                    isExpanded = false,
                    isCompactNarrow = isCompactNarrow,
                    homeListState = homeListState,
                    appsListState = appsListState,
                    trashScrollState = trashScrollState,
                    onOpenApps = openAppsTab,
                    onOpenSettings = handleOpenSettings,
                    onEvent = onEvent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                )
            }
        }

        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = fadeIn(tween(170)) + slideInVertically(
                tween(190, easing = SettingsOverlayEasing)
            ) { it / 20 },
            exit = fadeOut(tween(120))
        ) {
            val blockerInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HabNoirColors.ObsidianVoid)
                    .clickable(
                        indication = null,
                        interactionSource = blockerInteraction
                    ) {}
            ) {
                Scaffold(
                    containerColor = HabNoirColors.ObsidianVoid,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { HabNoirSnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    SettingsScreen(
                        state = state,
                        onEvent = onEvent,
                        onBack = onCloseSettings,
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    )
                }
            }
        }
    }
}

private val SettingsOverlayEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun TabDeckHost(
    deckState: TabDeckState,
    mountedMask: Int,
    state: MainUiState,
    iconCache: xyz.habnoir.clean.engine.scanner.AppIconMemoryCache,
    isExpanded: Boolean,
    isCompactNarrow: Boolean,
    homeListState: LazyListState,
    appsListState: LazyListState,
    trashScrollState: ScrollState,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (MainEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val slideDistancePx = with(LocalDensity.current) { 18.dp.toPx() }
    Box(modifier = modifier) {
        if ((mountedMask and (1 shl MainTab.SPACE.ordinal)) != 0) {
            TabSlot(
                tab = MainTab.SPACE,
                deckState = deckState,
                slideDistancePx = slideDistancePx
            ) {
                HomeScreen(
                    state = state,
                    iconCache = iconCache,
                    isExpanded = isExpanded,
                    isCompactNarrow = isCompactNarrow,
                    onEvent = onEvent,
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings,
                    listState = homeListState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if ((mountedMask and (1 shl MainTab.APPS.ordinal)) != 0) {
            TabSlot(
                tab = MainTab.APPS,
                deckState = deckState,
                slideDistancePx = slideDistancePx
            ) {
                AppsScreen(
                    state = state,
                    iconCache = iconCache,
                    onEvent = onEvent,
                    listState = appsListState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if ((mountedMask and (1 shl MainTab.TRASH.ordinal)) != 0) {
            TabSlot(
                tab = MainTab.TRASH,
                deckState = deckState,
                slideDistancePx = slideDistancePx
            ) {
                TrashScreen(
                    state = state,
                    onEvent = onEvent,
                    scrollState = trashScrollState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TabSlot(
    tab: MainTab,
    deckState: TabDeckState,
    slideDistancePx: Float,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val currentActive = deckState.activeTab
                    val currentExiting = deckState.exitingTab
                    val isActive = tab == currentActive
                    val isExiting = tab == currentExiting && !isActive
                    if (!isActive && !isExiting) {
                        return@layout
                    }
                    placeable.placeRelativeWithLayer(
                        x = 0,
                        y = 0,
                        zIndex = if (isActive) 1f else 0f
                    ) {
                        val p = deckState.effectiveProgress()
                        val dir = deckState.direction
                        if (isActive && currentExiting != null) {
                            alpha = p
                            translationX = (1f - p) * slideDistancePx * dir
                        } else if (isExiting) {
                            alpha = 1f - p
                            translationX = -p * slideDistancePx * dir
                        } else {
                            alpha = 1f
                            translationX = 0f
                        }
                    }
                }
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabNoirTopBar(
    deviceLabel: String,
    compactNarrow: Boolean,
    onOpenSettings: () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HabNoirColors.TonalFolio),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = HabNoirIcons.MonogramHN,
                            contentDescription = null,
                            tint = HabNoirColors.WarmAlabaster,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = HabNoirColors.WarmAlabaster,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = {
                if (!compactNarrow) {
                    Text(
                        text = deviceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = HabNoirColors.MutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HabNoirColors.TonalFolio)
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = HabNoirIcons.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = HabNoirColors.MutedSlate
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = HabNoirColors.DockedBar
            )
        )
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
    }
}

@Composable
private fun HabNoirBottomBar(
    deckState: TabDeckState,
    onSelectTab: (MainTab) -> Unit
) {
    val activeTab = deckState.activeTab
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
        NavigationBar(
            containerColor = HabNoirColors.DockedBar,
            tonalElevation = 0.dp
        ) {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = HabNoirColors.WarmAlabaster,
                selectedTextColor = HabNoirColors.WarmAlabaster,
                indicatorColor = HabNoirColors.WarmAlabaster.copy(alpha = 0.14f),
                unselectedIconColor = HabNoirColors.MutedSlate,
                unselectedTextColor = HabNoirColors.MutedSlate
            )
            NavigationBarItem(
                selected = activeTab == MainTab.SPACE,
                onClick = { onSelectTab(MainTab.SPACE) },
                icon = { Icon(HabNoirIcons.Space, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_space)) },
                colors = itemColors
            )
            NavigationBarItem(
                selected = activeTab == MainTab.APPS,
                onClick = { onSelectTab(MainTab.APPS) },
                icon = { Icon(HabNoirIcons.Apps, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_apps)) },
                colors = itemColors
            )
            NavigationBarItem(
                selected = activeTab == MainTab.TRASH,
                onClick = { onSelectTab(MainTab.TRASH) },
                icon = { Icon(HabNoirIcons.Trash, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_trash)) },
                colors = itemColors
            )
        }
    }
}

@Composable
private fun HabNoirNavRail(
    deckState: TabDeckState,
    onSelectTab: (MainTab) -> Unit
) {
    val activeTab = deckState.activeTab
    NavigationRail(containerColor = HabNoirColors.DockedBar) {
        val railColors = NavigationRailItemDefaults.colors(
            selectedIconColor = HabNoirColors.WarmAlabaster,
            selectedTextColor = HabNoirColors.WarmAlabaster,
            indicatorColor = HabNoirColors.WarmAlabaster.copy(alpha = 0.14f),
            unselectedIconColor = HabNoirColors.MutedSlate,
            unselectedTextColor = HabNoirColors.MutedSlate
        )
        NavigationRailItem(
            selected = activeTab == MainTab.SPACE,
            onClick = { onSelectTab(MainTab.SPACE) },
            icon = { Icon(HabNoirIcons.Space, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_space)) },
            colors = railColors
        )
        NavigationRailItem(
            selected = activeTab == MainTab.APPS,
            onClick = { onSelectTab(MainTab.APPS) },
            icon = { Icon(HabNoirIcons.Apps, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_apps)) },
            colors = railColors
        )
        NavigationRailItem(
            selected = activeTab == MainTab.TRASH,
            onClick = { onSelectTab(MainTab.TRASH) },
            icon = { Icon(HabNoirIcons.Trash, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_trash)) },
            colors = railColors
        )
    }
}

@Composable
private fun HabNoirSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = HabNoirColors.TonalFolio,
            contentColor = HabNoirColors.WarmAlabaster,
            actionColor = HabNoirColors.WarmChampagne
        )
    }
}
