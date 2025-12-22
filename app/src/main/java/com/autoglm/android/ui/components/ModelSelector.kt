package com.autoglm.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoglm.android.data.model.ModelConfig
import com.autoglm.android.ui.theme.*

/**
 * 模型选择器按钮
 * 显示当前选中的模型，点击打开选择菜单
 */
@Composable
fun ModelSelectorButton(
    currentModel: ModelConfig?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Grey50)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentModel?.displayName ?: "选择模型",
                style = ZiZipTypography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = Grey900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "展开",
                tint = Grey400,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 模型选择底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    models: List<ModelConfig>,
    currentModelId: String?,
    onModelSelected: (ModelConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = PrimaryWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择模型",
                style = ZiZipTypography.titleMedium,
                color = Grey900
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (models.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无可用模型\n请在设置中添加模型配置",
                        style = ZiZipTypography.bodyMedium,
                        color = Grey400
                    )
                }
            } else {
                models.forEach { model ->
                    ModelItem(
                        model = model,
                        isSelected = model.id == currentModelId,
                        onClick = {
                            onModelSelected(model)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: ModelConfig,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Accent.copy(alpha = 0.1f) else Grey50)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = ZiZipTypography.bodyLarge.copy(
                    fontWeight = if (isSelected) 
                        androidx.compose.ui.text.font.FontWeight.SemiBold 
                    else 
                        androidx.compose.ui.text.font.FontWeight.Normal
                ),
                color = if (isSelected) Accent else Grey900
            )
            if (model.modelName.isNotBlank()) {
                Text(
                    text = model.modelName,
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
            }
        }
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Accent)
            )
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}
