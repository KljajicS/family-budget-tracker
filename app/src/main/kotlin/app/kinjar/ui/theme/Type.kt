package app.kinjar.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kinjar.R

val Sans = FontFamily(
    Font(R.font.geist, FontWeight.Normal),
    Font(R.font.geist, FontWeight.Medium),
    Font(R.font.geist, FontWeight.SemiBold),
)

val Mono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Medium),
    Font(R.font.geist_mono, FontWeight.SemiBold),
)

fun sans(size: Int, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Sans, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking)

fun mono(size: Int, weight: FontWeight = FontWeight.Medium, tracking: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking)

val TitleStyle = sans(26, FontWeight.SemiBold, (-0.02).em)
val SectionStyle = sans(17, FontWeight.SemiBold)
val BodyStyle = sans(15)
val LabelStyle = sans(13)
val CaptionStyle = sans(12)
val BigAmountStyle = mono(40, FontWeight.SemiBold, (-0.03).em)
