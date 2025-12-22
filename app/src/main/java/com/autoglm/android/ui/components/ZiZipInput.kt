package com.autoglm.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.Grey400
import com.autoglm.android.ui.theme.Grey50
import com.autoglm.android.ui.theme.Grey900
import com.autoglm.android.ui.theme.ZiZipTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZiZipTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = true,
        textStyle = ZiZipTypography.bodyLarge,
        placeholder = {
            Text(text = placeholder, style = ZiZipTypography.bodyLarge, color = Grey400)
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = maxLines == 1,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Grey50,
            unfocusedContainerColor = Grey50,
            disabledContainerColor = Grey50,
            focusedBorderColor = Grey900,
            unfocusedBorderColor = Color.Transparent
        )
    )
}
