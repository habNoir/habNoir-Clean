package xyz.habnoir.clean.ui.screen

import android.content.res.Resources
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.engine.scanner.AppIconMemoryCache
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.model.CleanSessionState
import xyz.habnoir.clean.model.EngineTier
import xyz.habnoir.clean.ui.components.AppCacheLedgerRow
import xyz.habnoir.clean.ui.components.StorageStrataBar
import xyz.habnoir.clean.ui.theme.DataNumericStyle
import xyz.habnoir.clean.ui.theme.HabNoirColors
import xyz.habnoir.clean.ui.viewmodel.MainEvent
import xyz.habnoir.clean.ui.viewmodel.MainUiState

@Composable
fun HomeScreen(
    state: MainUiState,
    iconCache: AppIconMemoryCache,
    isExpanded: Boolean,
    isCompactNarrow: Boolean,
    onEvent: (MainEvent) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val resources = LocalResources.current
    val excludedTag = stringResource(R.string.app_excluded_tag)
    val onToggleWhitelist = remember(onEvent) { { pkg: String -> onEvent(MainEvent.ToggleWhitelist(pkg)) } }

    if (isExpanded) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeHeroSection(
                    state = state,
                    isCompactNarrow = false,
                    onEvent = onEvent,
                    onOpenSettings = onOpenSettings
                )
            }
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
            ) {
                HomeAppLedgerHeader(
                    totalAppsCount = state.allApps.size,
                    onOpenAllApps = onOpenApps
                )
                HomeIndependentAppList(
                    state = state,
                    iconCache = iconCache,
                    iconSizePx = iconSizePx,
                    excludedTag = excludedTag,
                    resources = resources,
                    listState = listState,
                    onToggleWhitelist = onToggleWhitelist,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            HomeHeroSection(
                state = state,
                isCompactNarrow = isCompactNarrow,
                onEvent = onEvent,
                onOpenSettings = onOpenSettings
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
            HomeAppLedgerHeader(
                totalAppsCount = state.allApps.size,
                onOpenAllApps = onOpenApps
            )
            HomeIndependentAppList(
                state = state,
                iconCache = iconCache,
                iconSizePx = iconSizePx,
                excludedTag = excludedTag,
                resources = resources,
                listState = listState,
                onToggleWhitelist = onToggleWhitelist,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HomeHeroSection(
    state: MainUiState,
    isCompactNarrow: Boolean,
    onEvent: (MainEvent) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!state.permissionHealth.hasUsageAccess) {
            Unit
        } else if (state.permissionHealth.isAndroid17Plus &&
            !state.permissionHealth.hasAccessibilityEnabled &&
            state.prefs.preferredEngineTier == EngineTier.ACCESSIBILITY_STAGED
        ) {
            ConditionalNoticeRow(
                title = stringResource(R.string.banner_a17_title),
                body = stringResource(R.string.banner_a17_body),
                actionLabel = stringResource(R.string.banner_a17_action),
                onAction = onOpenSettings
            )
        } else if (!state.permissionHealth.hasAccessibilityEnabled &&
            state.prefs.preferredEngineTier == EngineTier.ACCESSIBILITY_STAGED
        ) {
            ConditionalNoticeRow(
                title = stringResource(R.string.banner_setup_title),
                body = stringResource(R.string.banner_setup_body),
                actionLabel = stringResource(R.string.banner_setup_action),
                onAction = onOpenSettings
            )
        }

        val heroStyle = if (isCompactNarrow) {
            MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp, lineHeight = 38.sp)
        } else {
            MaterialTheme.typography.displayMedium
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HeroReclaimableCounter(
                bytes = state.combinedReclaimableBytes,
                style = heroStyle
            )
            val subtitleText = when {
                !state.permissionHealth.hasUsageAccess -> stringResource(R.string.hero_subtitle_needs_usage)
                state.isScanning && state.allApps.isEmpty() -> stringResource(R.string.home_scanning)
                state.combinedReclaimableBytes == 0L -> stringResource(
                    R.string.hero_subtitle_empty,
                    ByteFormat.format(state.prefs.minCacheThresholdBytes)
                )
                else -> stringResource(R.string.hero_subtitle_ready, state.cleanableApps.size)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isScanning) {
                    ScanPulseDot()
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabNoirColors.MutedSlate
                )
            }
        }

        StorageStrataBar(
            totalBytes = state.storageOverview.totalBytes,
            usedBytes = state.storageOverview.usedBytes,
            cacheBytes = state.cleanableCacheBytes,
            trashBytes = state.trashTotalBytes
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val session = state.cleanSessionState
            val isAnyCleaningRunning = session is CleanSessionState.Cleaning || session is CleanSessionState.Cooldown
            val isGuidedRunning = (session is CleanSessionState.Cleaning && session.isGuidedMode) ||
                (session is CleanSessionState.Cooldown && session.isGuidedMode)

            if (isGuidedRunning) {
                val activeAppTitle = if (session is CleanSessionState.Cleaning) {
                    "${session.activeAppLabel} (${session.processedApps}/${session.totalApps})"
                } else {
                    stringResource(R.string.cta_cleaning_active)
                }
                val isPaused = (session is CleanSessionState.Cleaning && session.isPaused) ||
                    (session is CleanSessionState.Cooldown && session.isPaused)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
                    Text(
                        text = activeAppTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = HabNoirColors.WarmAlabaster
                    )
                    Text(
                        text = stringResource(R.string.sheet_phase_guided),
                        style = MaterialTheme.typography.bodySmall,
                        color = HabNoirColors.WarmChampagne
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onEvent(if (isPaused) MainEvent.ResumeClean else MainEvent.PauseClean) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(if (isPaused) R.string.btn_resume else R.string.btn_pause),
                                color = HabNoirColors.WarmAlabaster
                            )
                        }
                        TextButton(
                            onClick = { onEvent(MainEvent.StopClean) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_stop),
                                color = HabNoirColors.MutedClayRose
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
                }
            }

            val primaryLabel = when {
                isAnyCleaningRunning -> stringResource(R.string.cta_cleaning_active)
                !state.permissionHealth.hasUsageAccess -> stringResource(R.string.cta_grant_usage)
                state.cleanableApps.isEmpty() -> stringResource(R.string.cta_rescan)
                else -> stringResource(R.string.cta_clean_staged)
            }
            val isActionReady = !isAnyCleaningRunning &&
                (state.cleanableApps.isNotEmpty() || !state.permissionHealth.hasUsageAccess)

            Button(
                onClick = {
                    if (!state.permissionHealth.hasUsageAccess) {
                        onEvent(MainEvent.OpenUsageAccessSettings)
                    } else if (state.cleanableApps.isEmpty()) {
                        onEvent(MainEvent.RefreshAll)
                    } else {
                        onEvent(MainEvent.StartStagedClean)
                    }
                },
                enabled = !isAnyCleaningRunning,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActionReady) HabNoirColors.WarmAlabaster else HabNoirColors.TonalFolio,
                    contentColor = if (isActionReady) HabNoirColors.ObsidianVoid else HabNoirColors.WarmAlabaster,
                    disabledContainerColor = HabNoirColors.TonalFolio,
                    disabledContentColor = HabNoirColors.MutedSlate
                )
            ) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.safety_reassurance),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.MutedSlate
                )
                Text(
                    text = stringResource(R.string.batch_meta_format, state.batchPolicy.batchSize),
                    style = DataNumericStyle.copy(color = HabNoirColors.QuietInk)
                )
            }
        }
    }
}

@Composable
private fun HeroReclaimableCounter(
    bytes: Long,
    style: TextStyle
) {
    val animatedHeroBytes by animateFloatAsState(
        targetValue = bytes.toFloat(),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "heroReclaimable"
    )
    Text(
        text = ByteFormat.format(animatedHeroBytes.toLong()),
        style = style,
        color = HabNoirColors.WarmAlabaster
    )
}

@Composable
private fun ScanPulseDot() {
    val scanTransition = rememberInfiniteTransition(label = "scanDot")
    val scanAlpha by scanTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanDotAlpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .graphicsLayer { alpha = scanAlpha }
            .clip(CircleShape)
            .background(HabNoirColors.WarmChampagne)
    )
}

@Composable
private fun ConditionalNoticeRow(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = HabNoirColors.WarmChampagne
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = HabNoirColors.MutedSlate
        )
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = HabNoirColors.WarmAlabaster
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
    }
}

@Composable
private fun HomeAppLedgerHeader(
    totalAppsCount: Int,
    onOpenAllApps: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_list_title),
            style = MaterialTheme.typography.titleMedium,
            color = HabNoirColors.WarmAlabaster
        )
        TextButton(
            onClick = onOpenAllApps,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.home_see_all_apps, totalAppsCount),
                style = MaterialTheme.typography.labelMedium,
                color = HabNoirColors.MutedSlate
            )
        }
    }
}

@Composable
private fun HomeIndependentAppList(
    state: MainUiState,
    iconCache: AppIconMemoryCache,
    iconSizePx: Int,
    excludedTag: String,
    resources: Resources,
    listState: LazyListState,
    onToggleWhitelist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleApps = state.homeVisibleApps
    if (visibleApps.isEmpty() && !state.isScanning) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.home_empty_list),
                style = MaterialTheme.typography.bodyMedium,
                color = HabNoirColors.MutedSlate
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            items = visibleApps,
            key = { item -> item.packageName.raw },
            contentType = { "home_app_row" }
        ) { item ->
            AppCacheLedgerRow(
                item = item,
                iconCache = iconCache,
                iconSizePx = iconSizePx,
                excludedTag = excludedTag,
                resources = resources,
                onToggleWhitelist = onToggleWhitelist
            )
        }
    }
}
