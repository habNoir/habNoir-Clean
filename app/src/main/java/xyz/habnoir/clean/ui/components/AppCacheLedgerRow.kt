package xyz.habnoir.clean.ui.components

import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.engine.scanner.AppIconMemoryCache
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.ui.icons.HabNoirIcons
import xyz.habnoir.clean.ui.theme.DataNumericStyle
import xyz.habnoir.clean.ui.theme.HabNoirColors

@Composable
fun AppCacheLedgerRow(
    item: AppCacheItem,
    iconCache: AppIconMemoryCache,
    iconSizePx: Int,
    excludedTag: String,
    resources: Resources,
    onToggleWhitelist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cachedBitmap = iconCache.getCached(item.packageName.raw)
    val iconBitmap = if (cachedBitmap != null) {
        cachedBitmap
    } else {
        val loaded by produceState<ImageBitmap?>(
            initialValue = null,
            key1 = item.packageName.raw
        ) {
            value = iconCache.getOrLoadIcon(item.packageName.raw, iconSizePx)
        }
        loaded
    }

    val isWhitelisted = item.isWhitelisted
    val toggleDesc = resources.getString(
        if (isWhitelisted) R.string.cd_include_app else R.string.cd_exclude_app,
        item.appLabel
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .graphicsLayer { alpha = if (isWhitelisted) 0.62f else 1f }
                .padding(vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HabNoirColors.TonalFolio),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.appLabel.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = HabNoirColors.WarmAlabaster
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = HabNoirColors.WarmAlabaster,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.formattedSize,
                            style = DataNumericStyle.copy(color = HabNoirColors.MutedSlate)
                        )
                        if (isWhitelisted) {
                            Text(
                                text = excludedTag,
                                style = MaterialTheme.typography.bodySmall,
                                color = HabNoirColors.WarmChampagne
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { onToggleWhitelist(item.packageName.raw) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isWhitelisted) HabNoirIcons.Lock else HabNoirIcons.LockOpen,
                    contentDescription = toggleDesc,
                    tint = if (isWhitelisted) HabNoirColors.WarmChampagne else HabNoirColors.MutedSlate
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = HabNoirColors.Hairline
        )
    }
}
