package com.autoglm.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.Grey150
import com.autoglm.android.ui.theme.PrimaryWhite
import com.autoglm.android.ui.theme.ZiZipTypography

@Composable
fun ZiZipCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = PrimaryWhite,
    borderColor: Color = Grey150,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
         content()
    }
}
