package xyz.habnoir.clean.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.habnoir.clean.R
import xyz.habnoir.clean.ui.theme.HabNoirColors
import xyz.habnoir.clean.ui.viewmodel.MainEvent
import xyz.habnoir.clean.ui.viewmodel.MainUiState
import kotlin.math.PI
import kotlin.math.sin

private val WizardStepEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun OnboardingScreen(
    state: MainUiState,
    onEvent: (MainEvent) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val isIndonesian = LocalLocale.current.platformLocale.language.let { it == "in" || it == "id" }
    val oemAdvice = if (isIndonesian) state.oemSpec.oemBatteryAdviceId else state.oemSpec.oemBatteryAdviceEn

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HabNoirColors.ObsidianVoid)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wiz_step_counter, step),
                    style = MaterialTheme.typography.labelMedium,
                    color = HabNoirColors.MutedSlate
                )
                TextButton(onClick = { onEvent(MainEvent.CompleteOnboarding) }) {
                    Text(
                        text = stringResource(R.string.cd_close),
                        color = HabNoirColors.WarmAlabaster
                    )
                }
            }
            WizardProgressTrack(currentStep = step)
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                val enterSlide = slideInHorizontally(
                    animationSpec = tween(220, easing = WizardStepEasing)
                ) { fullWidth -> if (forward) fullWidth / 10 else -fullWidth / 10 }
                val exitSlide = slideOutHorizontally(
                    animationSpec = tween(160, easing = WizardStepEasing)
                ) { fullWidth -> if (forward) -fullWidth / 14 else fullWidth / 14 }
                (fadeIn(tween(200)) + enterSlide)
                    .togetherWith(fadeOut(tween(130)) + exitSlide)
                    .using(SizeTransform(clip = false))
            },
            label = "wizardStepContent",
            modifier = Modifier.weight(1f)
        ) { targetStep ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                WizardAnimatedArtwork(
                    step = targetStep,
                    hasUsageAccess = state.permissionHealth.hasUsageAccess,
                    hasNotificationPermission = state.permissionHealth.hasNotificationPermission,
                    hasAccessibilityEnabled = state.permissionHealth.hasAccessibilityEnabled
                )
                Spacer(modifier = Modifier.height(22.dp))

                when (targetStep) {
                    1 -> {
                        Text(
                            text = stringResource(R.string.wiz1_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = HabNoirColors.WarmAlabaster
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.wiz1_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabNoirColors.MutedSlate
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.wiz1_point1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabNoirColors.WarmAlabaster
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.wiz1_point2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabNoirColors.WarmAlabaster
                        )
                    }

                    2 -> {
                        Text(
                            text = stringResource(R.string.wiz2_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = HabNoirColors.WarmAlabaster
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.wiz2_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabNoirColors.MutedSlate
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onEvent(MainEvent.OpenUsageAccessSettings) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                        ) {
                            Text(
                                text = if (state.permissionHealth.hasUsageAccess) {
                                    stringResource(R.string.status_ready)
                                } else {
                                    stringResource(R.string.wiz2_btn)
                                },
                                color = HabNoirColors.WarmAlabaster
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = HabNoirColors.Hairline)
                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = stringResource(R.string.wiz2_notif_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = HabNoirColors.WarmAlabaster
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.wiz2_notif_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabNoirColors.MutedSlate
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        val notifGranted = state.permissionHealth.hasNotificationPermission
                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            enabled = !notifGranted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (notifGranted) HabNoirColors.Hairline else HabNoirColors.ActiveHairline
                            )
                        ) {
                            Text(
                                text = stringResource(if (notifGranted) R.string.status_ready else R.string.wiz2_notif_btn),
                                color = if (notifGranted) HabNoirColors.MutedSlate else HabNoirColors.WarmAlabaster
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.wiz3_title, state.deviceIdentity.fullDisplay),
                            style = MaterialTheme.typography.titleLarge,
                            color = HabNoirColors.WarmAlabaster
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = oemAdvice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabNoirColors.MutedSlate
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(HabNoirColors.RaisedFolio)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.wiz3_restricted_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = HabNoirColors.WarmChampagne
                            )
                            Text(
                                text = stringResource(R.string.wiz3_restricted_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = HabNoirColors.MutedSlate
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEvent(MainEvent.OpenOwnAppInfo) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                            ) {
                                Text(
                                    text = stringResource(R.string.wiz3_btn_app_info),
                                    color = HabNoirColors.WarmAlabaster
                                )
                            }
                            OutlinedButton(
                                onClick = { onEvent(MainEvent.OpenAccessibilitySettings) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                            ) {
                                Text(
                                    text = stringResource(R.string.wiz3_btn_accessibility),
                                    color = HabNoirColors.WarmAlabaster
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, HabNoirColors.ActiveHairline)
                ) {
                    Text(
                        text = stringResource(R.string.wiz_btn_back),
                        color = HabNoirColors.WarmAlabaster
                    )
                }
            }
            Button(
                onClick = {
                    if (step < 3) step++ else onEvent(MainEvent.CompleteOnboarding)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HabNoirColors.WarmAlabaster,
                    contentColor = HabNoirColors.ObsidianVoid
                )
            ) {
                Text(
                    text = stringResource(if (step < 3) R.string.wiz_btn_next else R.string.wiz_btn_finish)
                )
            }
        }
    }
}

@Composable
private fun WizardProgressTrack(currentStep: Int) {
    val progress by animateFloatAsState(
        targetValue = (currentStep.coerceIn(1, 3)) / 3f,
        animationSpec = tween(260, easing = WizardStepEasing),
        label = "wizardProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .drawBehind {
                drawRect(color = HabNoirColors.TonalFolio)
                drawRect(
                    color = HabNoirColors.WarmChampagne,
                    size = Size(width = size.width * progress, height = size.height)
                )
            }
    )
}

@Composable
private fun WizardAnimatedArtwork(
    step: Int,
    hasUsageAccess: Boolean,
    hasNotificationPermission: Boolean,
    hasAccessibilityEnabled: Boolean
) {
    val transition = rememberInfiniteTransition(label = "wizardArtwork")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wizardPhase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = WizardStepEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wizardPulse"
    )

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(shape)
            .background(HabNoirColors.TonalFolio)
            .border(width = 0.5.dp, color = HabNoirColors.Hairline, shape = shape)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cr = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            val smallCr = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            // Subtle architectural grid lines
            val gridStep = w / 6f
            for (i in 1..5) {
                val gx = gridStep * i
                drawLine(
                    color = HabNoirColors.Hairline.copy(alpha = 0.45f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
            }

            when (step) {
                1 -> {
                    // Left Vault Panel: Protected User Data (Photos, Chats, Logins)
                    val leftW = w * 0.44f
                    drawRoundRect(
                        color = HabNoirColors.RaisedFolio,
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size = Size(leftW, h - 8.dp.toPx()),
                        cornerRadius = cr
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.25f + 0.25f * pulse),
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size = Size(leftW, h - 8.dp.toPx()),
                        cornerRadius = cr,
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    // Shield / Lock Emblem inside Vault
                    val lockCx = 24.dp.toPx()
                    val lockCy = 28.dp.toPx()
                    drawCircle(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.16f * pulse),
                        radius = 14.dp.toPx(),
                        center = Offset(lockCx, lockCy)
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmChampagne,
                        topLeft = Offset(lockCx - 6.dp.toPx(), lockCy - 2.dp.toPx()),
                        size = Size(12.dp.toPx(), 9.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                    drawArc(
                        color = HabNoirColors.WarmChampagne,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(lockCx - 4.dp.toPx(), lockCy - 8.dp.toPx()),
                        size = Size(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 3 Intact Protected Data Bars
                    val rowStartY = 54.dp.toPx()
                    val rowGap = 24.dp.toPx()
                    val barFractions = floatArrayOf(0.76f, 0.64f, 0.70f)
                    for (idx in 0..2) {
                        val ry = rowStartY + idx * rowGap
                        drawCircle(
                            color = HabNoirColors.WarmChampagne,
                            radius = 3.dp.toPx(),
                            center = Offset(18.dp.toPx(), ry + 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = HabNoirColors.WarmAlabaster.copy(alpha = 0.85f),
                            topLeft = Offset(28.dp.toPx(), ry),
                            size = Size((leftW - 40.dp.toPx()) * barFractions[idx], 8.dp.toPx()),
                            cornerRadius = smallCr
                        )
                    }

                    // Right Staged Batch Panel: 4 Cache Strata Bars dissolving in controlled wave
                    val rightX = w * 0.50f
                    val rightW = w - rightX
                    val batchGap = 28.dp.toPx()
                    val batchStartY = 16.dp.toPx()
                    for (b in 0..3) {
                        val by = batchStartY + b * batchGap
                        val localPhase = ((phase * 4f) - b).coerceIn(0f, 1f)
                        val remaining = 1f - CubicBezierEasing(0.4f, 0f, 0.2f, 1f).transform(localPhase)

                        // Track background
                        drawRoundRect(
                            color = HabNoirColors.RaisedFolio,
                            topLeft = Offset(rightX, by),
                            size = Size(rightW, 18.dp.toPx()),
                            cornerRadius = smallCr
                        )

                        // Shrinking cache fill
                        if (remaining > 0.03f) {
                            drawRoundRect(
                                color = if (b % 2 == 0) HabNoirColors.WarmAlabaster else HabNoirColors.WarmChampagne,
                                topLeft = Offset(rightX, by),
                                size = Size(rightW * (0.35f + 0.65f * remaining), 18.dp.toPx()),
                                cornerRadius = smallCr,
                                alpha = 0.25f + 0.75f * remaining
                            )
                        }
                    }
                }

                2 -> {
                    // Step 2: Storage Telemetry Scanner + Floating Notification Popup Preview
                    val radarW = w * 0.54f
                    val barY0 = 20.dp.toPx()
                    val barStep = 34.dp.toPx()
                    val baseWidths = floatArrayOf(0.88f, 0.65f, 0.48f)

                    for (i in 0..2) {
                        val y = barY0 + i * barStep
                        val wave = 0.92f + 0.08f * sin((phase * 2f * PI + i).toFloat())
                        drawRoundRect(
                            color = HabNoirColors.RaisedFolio,
                            topLeft = Offset(4.dp.toPx(), y),
                            size = Size(radarW, 20.dp.toPx()),
                            cornerRadius = smallCr
                        )
                        drawRoundRect(
                            color = if (hasUsageAccess) HabNoirColors.WarmChampagne else HabNoirColors.WarmAlabaster,
                            topLeft = Offset(4.dp.toPx(), y),
                            size = Size(radarW * baseWidths[i] * wave, 20.dp.toPx()),
                            cornerRadius = smallCr,
                            alpha = if (hasUsageAccess) 0.9f else 0.65f
                        )
                    }

                    // Sweeping vertical laser scanline
                    val scanX = 4.dp.toPx() + radarW * phase
                    drawLine(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.75f),
                        start = Offset(scanX, 12.dp.toPx()),
                        end = Offset(scanX, h - 12.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Right Floating Notification Card Preview
                    val cardX = w * 0.59f
                    val cardW = w - cardX
                    val cardH = h - 24.dp.toPx()
                    val cardY = 12.dp.toPx()
                    drawRoundRect(
                        color = HabNoirColors.RaisedFolio,
                        topLeft = Offset(cardX, cardY),
                        size = Size(cardW, cardH),
                        cornerRadius = cr
                    )
                    drawRoundRect(
                        color = if (hasNotificationPermission) {
                            HabNoirColors.WarmChampagne
                        } else {
                            HabNoirColors.ActiveHairline
                        },
                        topLeft = Offset(cardX, cardY),
                        size = Size(cardW, cardH),
                        cornerRadius = cr,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Pulsing live dot + mini notification bars
                    val dotX = cardX + 16.dp.toPx()
                    val dotY = cardY + 22.dp.toPx()
                    drawCircle(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.25f * pulse),
                        radius = 8.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                    drawCircle(
                        color = HabNoirColors.WarmChampagne,
                        radius = 4.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmAlabaster,
                        topLeft = Offset(dotX + 12.dp.toPx(), dotY - 4.dp.toPx()),
                        size = Size(cardW - 42.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = smallCr
                    )

                    // Animated progress bar inside notification preview
                    val progY = cardY + 52.dp.toPx()
                    val progW = cardW - 28.dp.toPx()
                    drawRoundRect(
                        color = HabNoirColors.ObsidianVoid,
                        topLeft = Offset(cardX + 14.dp.toPx(), progY),
                        size = Size(progW, 8.dp.toPx()),
                        cornerRadius = smallCr
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmChampagne,
                        topLeft = Offset(cardX + 14.dp.toPx(), progY),
                        size = Size(progW * (0.25f + 0.75f * phase), 8.dp.toPx()),
                        cornerRadius = smallCr
                    )

                    // Action pills at bottom of notification card
                    val pillY = cardY + cardH - 28.dp.toPx()
                    val pillW = (progW - 8.dp.toPx()) / 2f
                    drawRoundRect(
                        color = HabNoirColors.WarmAlabaster.copy(alpha = 0.18f),
                        topLeft = Offset(cardX + 14.dp.toPx(), pillY),
                        size = Size(pillW, 14.dp.toPx()),
                        cornerRadius = smallCr
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.35f),
                        topLeft = Offset(cardX + 22.dp.toPx() + pillW, pillY),
                        size = Size(pillW, 14.dp.toPx()),
                        cornerRadius = smallCr
                    )
                }

                else -> {
                    // Step 3: Android 13-17 Restricted Settings 3-Dot Menu -> Auto-Tap Clear Cache Blueprint
                    val panelW = w * 0.45f
                    // Left panel: App Info with top-right 3-dot menu pulsing
                    drawRoundRect(
                        color = HabNoirColors.RaisedFolio,
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size = Size(panelW, h - 8.dp.toPx()),
                        cornerRadius = cr
                    )
                    val menuX = panelW - 18.dp.toPx()
                    val menuY = 22.dp.toPx()
                    drawCircle(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.25f * pulse),
                        radius = 13.dp.toPx(),
                        center = Offset(menuX, menuY)
                    )
                    for (d in -1..1) {
                        drawCircle(
                            color = HabNoirColors.WarmChampagne,
                            radius = 2.dp.toPx(),
                            center = Offset(menuX, menuY + d * 4.5.dp.toPx())
                        )
                    }
                    // Dropdown popup "Izinkan setelan terbatas"
                    drawRoundRect(
                        color = HabNoirColors.ObsidianVoid,
                        topLeft = Offset(12.dp.toPx(), 42.dp.toPx()),
                        size = Size(panelW - 24.dp.toPx(), 32.dp.toPx()),
                        cornerRadius = smallCr
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmChampagne,
                        topLeft = Offset(12.dp.toPx(), 42.dp.toPx()),
                        size = Size(panelW - 24.dp.toPx(), 32.dp.toPx()),
                        cornerRadius = smallCr,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmAlabaster,
                        topLeft = Offset(20.dp.toPx(), 54.dp.toPx()),
                        size = Size(panelW - 44.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = smallCr
                    )

                    // Connector line from Left Panel to Right Auto-Tap Panel
                    val midX1 = panelW + 6.dp.toPx()
                    val midX2 = w * 0.54f - 6.dp.toPx()
                    drawLine(
                        color = HabNoirColors.WarmChampagne.copy(alpha = 0.6f),
                        start = Offset(midX1, h / 2f),
                        end = Offset(midX2, h / 2f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Right panel: Storage Page with Protected "Hapus Data" & Auto-Tapped "Hapus Cache"
                    val rightX = w * 0.54f
                    val rightW = w - rightX
                    drawRoundRect(
                        color = HabNoirColors.RaisedFolio,
                        topLeft = Offset(rightX, 4.dp.toPx()),
                        size = Size(rightW, h - 8.dp.toPx()),
                        cornerRadius = cr
                    )

                    val btnW = (rightW - 24.dp.toPx()) / 2f
                    val btnY = h - 42.dp.toPx()
                    val btnH = 26.dp.toPx()

                    // Left button: Hapus Data (guarded/Untouched in MutedClayRose outline)
                    val dangerX = rightX + 8.dp.toPx()
                    drawRoundRect(
                        color = HabNoirColors.MutedClayRose.copy(alpha = 0.14f),
                        topLeft = Offset(dangerX, btnY),
                        size = Size(btnW, btnH),
                        cornerRadius = smallCr
                    )
                    drawLine(
                        color = HabNoirColors.MutedClayRose.copy(alpha = 0.65f),
                        start = Offset(dangerX + 8.dp.toPx(), btnY + 6.dp.toPx()),
                        end = Offset(dangerX + btnW - 8.dp.toPx(), btnY + btnH - 6.dp.toPx()),
                        strokeWidth = 1.3.dp.toPx()
                    )

                    // Right button: Hapus Cache (Target with animated ripple ring)
                    val targetX = dangerX + btnW + 8.dp.toPx()
                    drawRoundRect(
                        color = if (hasAccessibilityEnabled) {
                            HabNoirColors.WarmChampagne
                        } else {
                            HabNoirColors.WarmAlabaster
                        },
                        topLeft = Offset(targetX, btnY),
                        size = Size(btnW, btnH),
                        cornerRadius = smallCr
                    )
                    val targetCenter = Offset(targetX + btnW / 2f, btnY + btnH / 2f)
                    val rippleRadius = (6.dp.toPx() + 18.dp.toPx() * phase)
                    drawCircle(
                        color = HabNoirColors.WarmChampagne.copy(alpha = (1f - phase) * 0.85f),
                        radius = rippleRadius,
                        center = targetCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Checkmark / status header bar inside right panel (zero-allocation drawLine)
                    val cx = rightX + 18.dp.toPx()
                    val cy = 28.dp.toPx()
                    drawLine(
                        color = HabNoirColors.WarmChampagne,
                        start = Offset(cx - 4.dp.toPx(), cy),
                        end = Offset(cx - 1.dp.toPx(), cy + 3.5.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = HabNoirColors.WarmChampagne,
                        start = Offset(cx - 1.dp.toPx(), cy + 3.5.dp.toPx()),
                        end = Offset(cx + 5.dp.toPx(), cy - 3.5.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawRoundRect(
                        color = HabNoirColors.WarmAlabaster.copy(alpha = 0.8f),
                        topLeft = Offset(rightX + 30.dp.toPx(), 24.dp.toPx()),
                        size = Size(rightW - 44.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = smallCr
                    )
                }
            }
        }
    }
}

