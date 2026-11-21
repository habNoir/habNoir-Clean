package xyz.habnoir.clean.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.engine.scanner.AppIconMemoryCache
import xyz.habnoir.clean.ui.components.AppCacheLedgerRow
import xyz.habnoir.clean.ui.icons.HabNoirIcons
import xyz.habnoir.clean.ui.theme.HabNoirColors
import xyz.habnoir.clean.ui.viewmodel.AppsFilter
import xyz.habnoir.clean.ui.viewmodel.MainEvent
import xyz.habnoir.clean.ui.viewmodel.MainUiState

@Composable
fun AppsScreen(
    state: MainUiState,
    iconCache: AppIconMemoryCache,
    onEvent: (MainEvent) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val resources = LocalResources.current
    val excludedTag = stringResource(R.string.app_excluded_tag)
    val onToggleWhitelist = remember(onEvent) { { pkg: String -> onEvent(MainEvent.ToggleWhitelist(pkg)) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.apps_title),
            style = MaterialTheme.typography.headlineSmall,
            color = HabNoirColors.WarmAlabaster
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.apps_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = HabNoirColors.MutedSlate
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = state.appsSearchQuery,
            onValueChange = { onEvent(MainEvent.UpdateSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.apps_search_hint),
                    color = HabNoirColors.QuietInk
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = HabNoirIcons.Search,
                    contentDescription = null,
                    tint = HabNoirColors.MutedSlate
                )
            },
            trailingIcon = {
                if (state.appsSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onEvent(MainEvent.UpdateSearchQuery("")) }) {
                        Icon(
                            imageVector = HabNoirIcons.Close,
                            contentDescription = stringResource(R.string.apps_clear_search),
                            tint = HabNoirColors.MutedSlate
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HabNoirColors.TonalFolio,
                unfocusedContainerColor = HabNoirColors.TonalFolio,
                focusedBorderColor = HabNoirColors.ActiveHairline,
                unfocusedBorderColor = HabNoirColors.Hairline,
                focusedTextColor = HabNoirColors.WarmAlabaster,
                unfocusedTextColor = HabNoirColors.WarmAlabaster,
                cursorColor = HabNoirColors.WarmAlabaster
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppsFilterChip(
                selected = state.appsFilter == AppsFilter.ALL,
                label = stringResource(R.string.filter_all),
                onClick = { onEvent(MainEvent.UpdateAppsFilter(AppsFilter.ALL)) }
            )
            AppsFilterChip(
                selected = state.appsFilter == AppsFilter.OVER_150_MB,
                label = stringResource(R.string.filter_large),
                onClick = { onEvent(MainEvent.UpdateAppsFilter(AppsFilter.OVER_150_MB)) }
            )
            AppsFilterChip(
                selected = state.appsFilter == AppsFilter.EXCLUDED,
                label = stringResource(R.string.filter_locked),
                onClick = { onEvent(MainEvent.UpdateAppsFilter(AppsFilter.EXCLUDED)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)

        if (state.filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 28.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.apps_empty_search),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HabNoirColors.MutedSlate
                    )
                    if (state.appsSearchQuery.isNotEmpty() || state.appsFilter != AppsFilter.ALL) {
                        TextButton(
                            onClick = {
                                onEvent(MainEvent.UpdateSearchQuery(""))
                                onEvent(MainEvent.UpdateAppsFilter(AppsFilter.ALL))
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.apps_clear_search),
                                color = HabNoirColors.WarmAlabaster
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = state.filteredApps,
                    key = { item -> item.packageName.raw },
                    contentType = { "app_cache_row" }
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
    }
}

@Composable
private fun AppsFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
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
