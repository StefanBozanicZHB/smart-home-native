package zhb.company.smart_home.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Port of the Flutter app's CurveClipper - a curved-bottom header shape. */
val CurvedHeaderShape = GenericShape { size, _ ->
    val curveHeight = 40f
    lineTo(0f, size.height - curveHeight)
    quadraticTo(
        size.width / 2f, size.height + curveHeight,
        size.width, size.height - curveHeight,
    )
    lineTo(size.width, 0f)
    close()
}

/**
 * Curved teal header, matching every Flutter room screen: a hero room photo clipped to the
 * curve, a teal scrim so overlaid [StatBadge]s stay legible, and a slot for those badges.
 */
@Composable
fun CurvedHeader(
    modifier: Modifier = Modifier,
    @DrawableRes imageRes: Int? = null,
    heightDp: Dp = 200.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(CurvedHeaderShape)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
        }
        content()
    }
}
