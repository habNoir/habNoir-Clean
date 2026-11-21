package xyz.habnoir.clean.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.data.prefs.UserPreferencesSnapshot
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.model.EngineTier
import xyz.habnoir.clean.ui.icons.HabNoirIcons
import xyz.habnoir.clean.ui.theme.HabNoirColors
import xyz.habnoir.clean.ui.viewmodel.MainEvent
import xyz.habnoir.clean.ui.viewmodel.MainUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onEvent: (MainEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIndonesian = LocalLocale.current.platformLocale.language.let { it == "in" || it == "id" }
    val oemAdvice = if (isIndonesian) state.oemSpec.oemBatteryAdviceId else state.oemSpec.oemBatteryAdviceEn

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = HabNoirColors.WarmAlabaster
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = HabNoirIcons.Back,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = HabNoirColors.WarmAlabaster
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = HabNoirColors.DockedBar
            )
        )
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Engine Mode
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_section_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = HabNoirColors.MutedSlate
                )
                EngineModeOptionRow(
                    selected = state.prefs.preferredEngineTier == EngineTier.ACCESSIBILITY_STAGED,
                    title = stringResource(R.string.mode_accessibility_title),
                    desc = stringResource(R.string.mode_accessibility_desc),
                    onClick = { onEvent(MainEvent.SetEngineTier(EngineTier.ACCESSIBILITY_STAGED)) }
                )
                EngineModeOptionRow(
                    selected = state.prefs.preferredEngineTier == EngineTier.GUIDED_QUEUE,
                    title = stringResource(R.string.mode_guided_title),
                    desc = stringResource(R.string.mode_guided_desc),
                    onClick = { onEvent(MainEvent.SetEngineTier(EngineTier.GUIDED_QUEUE)) }
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)

            // 2. Threshold & Reminder
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_section_threshold),
                    style = MaterialTheme.typography.labelLarge,
                    color = HabNoirColors.MutedSlate
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThresholdChip(
                        label = stringResource(R.string.threshold_1mb),
                        selected = state.prefs.minCacheThresholdBytes == UserPreferencesSnapshot.THRESHOLD_1_MB,
                        onClick = { onEvent(MainEvent.SetMinThresholdBytes(UserPreferencesSnapshot.THRESHOLD_1_MB)) }
                    )
                    ThresholdChip(
                        label = stringResource(R.string.threshold_5mb),
                        selected = state.prefs.minCacheThresholdBytes == UserPreferencesSnapshot.DEFAULT_THRESHOLD_BYTES,
                        onClick = { onEvent(MainEvent.SetMinThresholdBytes(UserPreferencesSnapshot.DEFAULT_THRESHOLD_BYTES)) }
                    )
                    ThresholdChip(
                        label = stringResource(R.string.threshold_10mb),
                        selected = state.prefs.minCacheThresholdBytes == UserPreferencesSnapshot.THRESHOLD_10_MB,
                        onClick = { onEvent(MainEvent.SetMinThresholdBytes(UserPreferencesSnapshot.THRESHOLD_10_MB)) }
                    )
                }

                val cooldownFormatted = String.format(
                    Locale.US,
                    "%.1fs",
                    state.batchPolicy.batchCooldownMs / 1000.0
                )
                Text(
                    text = stringResource(
                        R.string.settings_batch_policy_info,
                        state.batchPolicy.batchSize,
                        cooldownFormatted
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.MutedSlate
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_reminder_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = HabNoirColors.WarmAlabaster
                        )
                        Text(
                            text = stringResource(R.string.settings_reminder_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = HabNoirColors.MutedSlate
                        )
                    }
                    Switch(
                        checked = state.prefs.reminderEnabled,
                        onCheckedChange = { onEvent(MainEvent.SetReminderEnabled(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HabNoirColors.ObsidianVoid,
                            checkedTrackColor = HabNoirColors.WarmAlabaster
                        )
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)

            // 3. OEM Readiness Checklist
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_oem),
                        style = MaterialTheme.typography.labelLarge,
                        color = HabNoirColors.MutedSlate
                    )
                    Text(
                        text = state.deviceIdentity.fullDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        color = HabNoirColors.WarmChampagne
                    )
                }

                Text(
                    text = oemAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.MutedSlate
                )
                Text(
                    text = stringResource(R.string.settings_target_button, state.oemSpec.primaryTargetLabelDisplay),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.WarmAlabaster
                )

                PermissionCheckRow(
                    title = stringResource(R.string.check_usage_access),
                    ready = state.permissionHealth.hasUsageAccess,
                    onFix = { onEvent(MainEvent.OpenUsageAccessSettings) }
                )
                PermissionCheckRow(
                    title = stringResource(R.string.check_accessibility),
                    ready = state.permissionHealth.hasAccessibilityEnabled,
                    onFix = { onEvent(MainEvent.OpenAccessibilitySettings) }
                )
                PermissionCheckRow(
                    title = stringResource(R.string.check_battery_oem),
                    ready = state.permissionHealth.isIgnoringBatteryOptimizations,
                    onFix = { onEvent(MainEvent.OpenBatteryOemSettings) }
                )

                OutlinedButton(
                    onClick = { onEvent(MainEvent.ReopenOnboarding) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                ) {
                    Text(
                        text = stringResource(R.string.action_open_guide),
                        color = HabNoirColors.WarmAlabaster
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)

            // 4. Privacy & Stats
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_privacy_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = HabNoirColors.WarmAlabaster
                )
                Text(
                    text = stringResource(R.string.settings_privacy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.MutedSlate
                )
                Text(
                    text = stringResource(
                        R.string.settings_total_freed,
                        ByteFormat.format(state.prefs.totalFreedBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.WarmChampagne
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EngineModeOptionRow(
    selected: Boolean,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HabNoirColors.RaisedFolio)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = HabNoirColors.WarmAlabaster
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = HabNoirColors.MutedSlate
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = HabNoirColors.WarmAlabaster,
                unselectedColor = HabNoirColors.MutedSlate
            )
        )
    }
}

@Composable
private fun ThresholdChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(10.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = HabNoirColors.Hairline,
            selectedBorderColor = HabNoirColors.WarmAlabaster
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = HabNoirColors.TonalFolio,
            labelColor = HabNoirColors.MutedSlate,
            selectedContainerColor = HabNoirColors.WarmAlabaster,
            selectedLabelColor = HabNoirColors.ObsidianVoid
        )
    )
}

@Composable
private fun PermissionCheckRow(
    title: String,
    ready: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = HabNoirColors.WarmAlabaster
            )
            Text(
                text = stringResource(if (ready) R.string.status_ready else R.string.status_needed),
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) HabNoirColors.MutedSlate else HabNoirColors.WarmChampagne
            )
        }
        if (!ready) {
            TextButton(onClick = onFix) {
                Text(
                    text = stringResource(R.string.action_fix),
                    color = HabNoirColors.WarmAlabaster
                )
            }
        }
    }
}
