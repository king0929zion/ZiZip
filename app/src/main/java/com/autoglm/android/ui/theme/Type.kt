package com.autoglm.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.autoglm.android.R

val ResourceHanRounded = FontFamily(
    Font(R.font.resource_han_rounded_cn_regular, FontWeight.Normal),
    Font(R.font.resource_han_rounded_cn_medium, FontWeight.Medium),
    Font(R.font.resource_han_rounded_cn_bold, FontWeight.Bold)
)

val ZiZipTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = ResourceHanRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = PrimaryBlack
    ),
    headlineMedium = TextStyle( // For standard headings
        fontFamily = ResourceHanRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = PrimaryBlack
    ),
    titleMedium = TextStyle(
        fontFamily = ResourceHanRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = PrimaryBlack
    ),
    bodyLarge = TextStyle(
        fontFamily = ResourceHanRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = Grey700
    ),
    bodyMedium = TextStyle( // Default body
        fontFamily = ResourceHanRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Grey700
    ),
    labelSmall = TextStyle( // Caption
        fontFamily = ResourceHanRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Grey400
    )
)
