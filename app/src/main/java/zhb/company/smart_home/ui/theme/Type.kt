package zhb.company.smart_home.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import zhb.company.smart_home.R

val FiraSans = FontFamily(Font(R.font.fira_sans_regular))

/** Matches AppStyles.getFiraTextStyle - white badge/overlay text over room header images. */
@Composable
fun badgeTextStyle(): TextStyle = TextStyle(
    fontFamily = FiraSans,
    color = TextColor,
    fontSize = 14.sp,
)

val AppTypography = Typography()
