package zhb.company.smart_home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White
import zhb.company.smart_home.ui.theme.badgeTextStyle

/** Port of AppStyles.getBoxDecorationHeader - teal (or red when disabled) semi-transparent badge. */
@Composable
fun StatBadge(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    isDisabled: Boolean = false,
) {
    Row(
        modifier = modifier
            .background(
                color = (if (isDisabled) DisableRed else PrimaryColor).copy(alpha = 0.8f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
        Text(text = text, style = badgeTextStyle(), modifier = Modifier.padding(start = 6.dp))
    }
}
