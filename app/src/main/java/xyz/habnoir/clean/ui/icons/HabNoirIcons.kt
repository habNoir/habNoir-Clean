package xyz.habnoir.clean.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object HabNoirIcons {

    val Space: ImageVector by lazy {
        strokeIcon("Space") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
                moveTo(3f, 10f)
                lineTo(21f, 10f)
            }
        }
    }

    val Apps: ImageVector by lazy {
        strokeIcon("Apps") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
        }
    }

    val Trash: ImageVector by lazy {
        strokeIcon("Trash") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(8f, 6f); lineTo(8f, 4f); lineTo(16f, 4f); lineTo(16f, 6f)
                moveTo(6f, 6f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 6f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 9f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 15f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 9f)
                moveTo(12f, 2f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 22f)
                moveTo(4.9f, 4.9f); lineTo(7.1f, 7.1f)
                moveTo(16.9f, 16.9f); lineTo(19.1f, 19.1f)
                moveTo(2f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(22f, 12f)
                moveTo(4.9f, 19.1f); lineTo(7.1f, 16.9f)
                moveTo(16.9f, 7.1f); lineTo(19.1f, 4.9f)
            }
        }
    }

    val Back: ImageVector by lazy {
        strokeIcon("Back") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 11f); lineTo(20f, 11f); lineTo(20f, 21f); lineTo(4f, 21f); close()
                moveTo(8f, 11f); lineTo(8f, 7f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 7f)
                lineTo(16f, 11f)
            }
        }
    }

    val LockOpen: ImageVector by lazy {
        strokeIcon("LockOpen") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 11f); lineTo(20f, 11f); lineTo(20f, 21f); lineTo(4f, 21f); close()
                moveTo(8f, 11f); lineTo(8f, 7f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.6f, 5.5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11f, 4f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 18f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 4f)
                moveTo(20f, 20f); lineTo(16f, 16f)
            }
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    val MonogramHN: ImageVector by lazy {
        ImageVector.Builder(
            name = "MonogramHN",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 64f,
            viewportHeight = 64f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 3.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(14f, 14f); lineTo(14f, 50f)
                moveTo(26f, 10f); lineTo(26f, 54f)
                moveTo(14f, 32f); curveTo(22f, 23f, 30f, 25f, 46f, 46f)
                moveTo(46f, 16f); lineTo(46f, 48f)
                moveTo(34f, 38f); lineTo(34f, 50f)
            }
        }.build()
    }

    private inline fun strokeIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()
}
