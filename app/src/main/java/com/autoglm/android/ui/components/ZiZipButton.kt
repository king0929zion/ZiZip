package com.autoglm.android.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.Grey100
import com.autoglm.android.ui.theme.PrimaryBlack
import com.autoglm.android.ui.theme.PrimaryWhite
import com.autoglm.android.ui.theme.ZiZipTypography

@Composable
fun ZiZipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false
) {
    val containerColor = if (isSecondary) Grey100 else PrimaryBlack
    val contentColor = if (isSecondary) PrimaryBlack else PrimaryWhite

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        enabled = enabled,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Text(
            text = text,
            style = ZiZipTypography.titleMedium
        )
    }
}
