package vn.hutech.foodexpress.driver.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal data class ArtworkCrop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

@Composable
internal fun ReferenceArtwork(
    imageResource: Int,
    crop: ArtworkCrop,
    contentDescription: String,
    modifier: Modifier = Modifier,
    horizontalBias: Float = 0f,
    verticalBias: Float = 0f,
) {
    val image = ImageBitmap.imageResource(imageResource)

    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        drawFittedImage(
            image = image,
            crop = crop,
            horizontalBias = horizontalBias,
            verticalBias = verticalBias,
        )
    }
}

private fun DrawScope.drawFittedImage(
    image: ImageBitmap,
    crop: ArtworkCrop,
    horizontalBias: Float,
    verticalBias: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return

    val scale = minOf(
        size.width / crop.width,
        size.height / crop.height,
    )
    val destinationWidth = (crop.width * scale).roundToInt()
    val destinationHeight = (crop.height * scale).roundToInt()
    val xFraction = (horizontalBias.coerceIn(-1f, 1f) + 1f) / 2f
    val yFraction = (verticalBias.coerceIn(-1f, 1f) + 1f) / 2f
    val destinationLeft = ((size.width - destinationWidth) * xFraction).roundToInt()
    val destinationTop = ((size.height - destinationHeight) * yFraction).roundToInt()

    drawImage(
        image = image,
        srcOffset = IntOffset(crop.left, crop.top),
        srcSize = IntSize(crop.width, crop.height),
        dstOffset = IntOffset(destinationLeft, destinationTop),
        dstSize = IntSize(destinationWidth, destinationHeight),
        filterQuality = FilterQuality.High,
    )
}
