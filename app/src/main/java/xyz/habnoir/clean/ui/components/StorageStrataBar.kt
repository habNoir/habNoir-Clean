package xyz.habnoir.clean.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.ui.theme.DataNumericStyle
import xyz.habnoir.clean.ui.theme.HabNoirColors

@Composable
fun StorageStrataBar(
    totalBytes: Long,
    usedBytes: Long,
    cacheBytes: Long,
    trashBytes: Long,
    modifier: Modifier = Modifier
) {
    val privateBytes = maxOf(0L, usedBytes - cacheBytes - trashBytes)
    val safeTotal = maxOf(1L, totalBytes)
    val cacheLabel = ByteFormat.format(cacheBytes)
    val trashLabel = ByteFormat.format(trashBytes)
    val privateLabel = ByteFormat.format(privateBytes)
    val strataDesc = "$privateLabel, $cacheLabel, $trashLabel"

    val animSysFrac by animateFloatAsState(
        targetValue = (privateBytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "sysFrac"
    )
    val animCacheFrac by animateFloatAsState(
        targetValue = (cacheBytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "cacheFrac"
    )
    val animTrashFrac by animateFloatAsState(
        targetValue = (trashBytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "trashFrac"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = strataDesc }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .drawBehind {
                    val gapPx = 1.dp.toPx()
                    val totalW = size.width
                    val h = size.height

                    val cacheW = if (cacheBytes > 0L) maxOf(totalW * animCacheFrac, 6.dp.toPx()) else 0f
                    val trashW = if (trashBytes > 0L) maxOf(totalW * animTrashFrac, 4.dp.toPx()) else 0f
                    val maxSysW = maxOf(0f, totalW - cacheW - trashW - gapPx * 2)
                    val sysW = minOf(totalW * animSysFrac, maxSysW)

                    drawRect(color = HabNoirColors.TonalFolio)
                    var x = 0f
                    if (sysW > 0f) {
                        drawRect(
                            color = HabNoirColors.StrataSystem,
                            topLeft = Offset(x, 0f),
                            size = Size(sysW, h)
                        )
                        x += sysW + gapPx
                    }
                    if (cacheW > 0f && x < totalW) {
                        drawRect(
                            color = HabNoirColors.WarmAlabaster,
                            topLeft = Offset(x, 0f),
                            size = Size(minOf(cacheW, totalW - x), h)
                        )
                        x += cacheW + gapPx
                    }
                    if (trashW > 0f && x < totalW) {
                        drawRect(
                            color = HabNoirColors.WarmChampagne,
                            topLeft = Offset(x, 0f),
                            size = Size(minOf(trashW, totalW - x), h)
                        )
                    }
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StrataLegendDot(
                color = HabNoirColors.StrataSystem,
                text = stringResource(R.string.strata_label_private, privateLabel)
            )
            StrataLegendDot(
                color = HabNoirColors.WarmAlabaster,
                text = stringResource(R.string.strata_label_cache, cacheLabel)
            )
            StrataLegendDot(
                color = HabNoirColors.WarmChampagne,
                text = stringResource(R.string.strata_label_trash, trashLabel)
            )
        }
    }
}

@Composable
private fun StrataLegendDot(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .drawWithCache {
                    onDrawBehind { drawRect(color = color) }
                }
        )
        Text(
            text = text,
            style = DataNumericStyle.copy(color = HabNoirColors.MutedSlate)
        )
    }
}
