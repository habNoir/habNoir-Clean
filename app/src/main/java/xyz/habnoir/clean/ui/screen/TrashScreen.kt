package xyz.habnoir.clean.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.ui.theme.DataNumericStyle
import xyz.habnoir.clean.ui.theme.HabNoirColors
import xyz.habnoir.clean.ui.viewmodel.MainEvent
import xyz.habnoir.clean.ui.viewmodel.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrashScreen(
    state: MainUiState,
    onEvent: (MainEvent) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    val snapshot = state.trashSnapshot
    val layer1 = snapshot?.layer1

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.trash_title),
                style = MaterialTheme.typography.headlineSmall,
                color = HabNoirColors.WarmAlabaster
            )
            Text(
                text = stringResource(R.string.trash_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = HabNoirColors.MutedSlate
            )
        }

        // Layer 1: Universal MediaStore Trash
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trash_layer1_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = HabNoirColors.WarmAlabaster
                )
                val l1Count = layer1?.fileCount ?: 0
                val l1Bytes = layer1?.totalBytes ?: 0L
                Text(
                    text = if (l1Count > 0) {
                        stringResource(R.string.trash_layer1_status, l1Count, ByteFormat.format(l1Bytes))
                    } else {
                        stringResource(R.string.trash_layer1_empty)
                    },
                    style = DataNumericStyle.copy(
                        color = if (l1Count > 0) HabNoirColors.WarmChampagne else HabNoirColors.MutedSlate
                    )
                )
            }

            Text(
                text = stringResource(R.string.trash_layer1_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = HabNoirColors.MutedSlate
            )

            val hasLayer1 = (layer1?.fileCount ?: 0) > 0
            Button(
                onClick = { onEvent(MainEvent.PurgeLayer1NextChunk) },
                enabled = hasLayer1,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HabNoirColors.WarmAlabaster,
                    contentColor = HabNoirColors.ObsidianVoid,
                    disabledContainerColor = HabNoirColors.TonalFolio,
                    disabledContentColor = HabNoirColors.QuietInk
                )
            ) {
                Text(
                    text = if (hasLayer1) {
                        stringResource(R.string.trash_layer1_btn, ByteFormat.format(layer1?.totalBytes ?: 0L))
                    } else {
                        stringResource(R.string.trash_layer1_btn_done)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Layer 2: Deterministic Hidden Trash Allowlist
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trash_layer2_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = HabNoirColors.WarmAlabaster
                )
                Text(
                    text = stringResource(R.string.trash_layer2_tag),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.QuietInk
                )
            }

            val folderHint = if (snapshot?.layer2Folders?.isNotEmpty() == true) {
                snapshot.layer2Folders.joinToString(", ") { it.relativePath }
            } else {
                ".Trash, .recycle, .trashed"
            }

            Text(
                text = stringResource(R.string.trash_layer2_desc, folderHint),
                style = MaterialTheme.typography.bodyMedium,
                color = HabNoirColors.MutedSlate
            )

            val hasAllFiles = snapshot?.layer2HasAccess == true
            val l2Count = snapshot?.layer2Files?.size ?: 0
            val l2Bytes = snapshot?.layer2TotalBytes ?: 0L

            if (!hasAllFiles) {
                OutlinedButton(
                    onClick = { onEvent(MainEvent.OpenAllFilesSettings) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                ) {
                    Text(
                        text = stringResource(R.string.trash_layer2_grant_btn),
                        color = HabNoirColors.WarmAlabaster
                    )
                }
            } else if (l2Count > 0) {
                Button(
                    onClick = { onEvent(MainEvent.PurgeLayer2Files) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HabNoirColors.WarmAlabaster,
                        contentColor = HabNoirColors.ObsidianVoid
                    )
                ) {
                    Text(
                        text = stringResource(
                            R.string.trash_layer2_scan_btn,
                            l2Count,
                            ByteFormat.format(l2Bytes)
                        )
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.trash_layer2_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabNoirColors.MutedSlate
                )
            }
        }

        // Layer 3: Built-in OEM Gallery Shortcuts
        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.trash_layer3_title),
                style = MaterialTheme.typography.titleMedium,
                color = HabNoirColors.WarmAlabaster
            )
            Text(
                text = stringResource(R.string.trash_layer3_desc_default),
                style = MaterialTheme.typography.bodyMedium,
                color = HabNoirColors.MutedSlate
            )

            val galleries = snapshot?.layer3Galleries.orEmpty()
            if (galleries.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (gallery in galleries) {
                        OutlinedButton(
                            onClick = { onEvent(MainEvent.OpenGalleryShortcut(gallery.packageName)) },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                        ) {
                            Text(
                                text = gallery.label,
                                color = HabNoirColors.WarmAlabaster
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
