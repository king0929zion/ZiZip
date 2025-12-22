package com.autoglm.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Notion 风格设计系统
 * 极简、内容优先、排版至上
 */
object NotionTheme {
    
    // ==================== 色彩系统 ====================
    
    object Colors {
        // 背景色
        val Background = Color(0xFFFFFFFF)
        val BackgroundSecondary = Color(0xFFFBFBFA)
        val BackgroundHover = Color(0xFFF7F6F3)
        val BackgroundSelected = Color(0xFFE8F0FE)
        val BackgroundCode = Color(0xFFF7F6F3)
        
        // 文字色
        val TextPrimary = Color(0xFF37352F)
        val TextSecondary = Color(0xFF787774)
        val TextPlaceholder = Color(0xFF9B9A97)
        val TextDisabled = Color(0xFFCECFCD)
        
        // 边框色
        val Border = Color(0xFFE9E9E7)
        val BorderStrong = Color(0xFF37352F)
        val Divider = Color(0xFFF0F0EE)
        
        // 强调色 - 蓝色
        val Accent = Color(0xFF2383E2)
        val AccentHover = Color(0xFF1B6FC2)
        val AccentLight = Color(0xFFE8F0FE)
        
        // 功能色
        val Success = Color(0xFF0F7B6C)
        val SuccessLight = Color(0xFFDBEDDB)
        val Warning = Color(0xFFD9730D)
        val WarningLight = Color(0xFFFDECC8)
        val Error = Color(0xFFE03E3E)
        val ErrorLight = Color(0xFFFFE2DD)
        val Info = Color(0xFF6940A5)
        val InfoLight = Color(0xFFE8DEEE)
        
        // 标签色板 (Notion 风格)
        val TagGray = Color(0xFFE3E2E0)
        val TagBrown = Color(0xFFEEE0DA)
        val TagOrange = Color(0xFFFDECC8)
        val TagYellow = Color(0xFFFBF3DB)
        val TagGreen = Color(0xFFDBEDDB)
        val TagBlue = Color(0xFFD3E5EF)
        val TagPurple = Color(0xFFE8DEEE)
        val TagPink = Color(0xFFF4DFEB)
        val TagRed = Color(0xFFFFE2DD)
        
        // 深色模式
        object Dark {
            val Background = Color(0xFF191919)
            val BackgroundSecondary = Color(0xFF202020)
            val BackgroundHover = Color(0xFF2D2D2D)
            val TextPrimary = Color(0xFFE6E6E6)
            val TextSecondary = Color(0xFF9B9B9B)
            val TextPlaceholder = Color(0xFF6B6B6B)
            val Border = Color(0xFF373737)
            val Accent = Color(0xFF529CCA)
        }
    }
    
    // ==================== 字体系统 ====================
    
    object Typography {
        // 标题
        val H1 = TextStyle(
            fontSize = 40.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.TextPrimary
        )
        
        val H2 = TextStyle(
            fontSize = 30.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.SemiBold,
            color = Colors.TextPrimary
        )
        
        val H3 = TextStyle(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = Colors.TextPrimary
        )
        
        val H4 = TextStyle(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = Colors.TextPrimary
        )
        
        // 正文
        val Body = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary
        )
        
        val BodySmall = TextStyle(
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary
        )
        
        // 标签
        val Caption = TextStyle(
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextSecondary
        )
        
        val Label = TextStyle(
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.TextSecondary
        )
        
        // 代码
        val Code = TextStyle(
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary
        )
    }
    
    // ==================== 形状系统 ====================
    
    object Shapes {
        val None = RoundedCornerShape(0.dp)
        val ExtraSmall = RoundedCornerShape(2.dp)
        val Small = RoundedCornerShape(4.dp)
        val Medium = RoundedCornerShape(6.dp)
        val Large = RoundedCornerShape(8.dp)
        val ExtraLarge = RoundedCornerShape(12.dp)
        val Full = RoundedCornerShape(50)
    }
    
    // ==================== 间距系统 ====================
    
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
        val xxl = 48.dp
    }
    
    // ==================== 阴影系统 ====================
    
    object Elevation {
        val none = 0.dp
        val subtle = 1.dp  // 几乎看不见
        val small = 2.dp   // 微弱阴影
        val medium = 4.dp  // 弹窗、下拉
    }
    
    // ==================== 尺寸系统 ====================
    
    object Sizes {
        // 按钮高度
        val ButtonSmall = 28.dp
        val ButtonMedium = 32.dp
        val ButtonLarge = 40.dp
        
        // 输入框高度
        val InputSmall = 32.dp
        val InputMedium = 40.dp
        
        // 图标尺寸
        val IconSmall = 16.dp
        val IconMedium = 20.dp
        val IconLarge = 24.dp
        val IconXLarge = 32.dp
        
        // 头像尺寸
        val AvatarSmall = 24.dp
        val AvatarMedium = 32.dp
        val AvatarLarge = 44.dp
        
        // 列表项高度
        val ListItemSmall = 32.dp
        val ListItemMedium = 40.dp
        val ListItemLarge = 48.dp
    }
    
    // ==================== 动画时长 ====================
    
    object Animation {
        const val Fast = 100
        const val Normal = 200
        const val Slow = 300
    }
}
