package com.carcare.analytics.core.designsystem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Terracotta = lightColorScheme(
 primary=Color(0xFFAF583D), onPrimary=Color.White, primaryContainer=Color(0xFFF4E2D8), onPrimaryContainer=Color(0xFF713923),
 secondary=Color(0xFF8D6554), onSecondary=Color.White, secondaryContainer=Color(0xFFF4E8E1), onSecondaryContainer=Color(0xFF633E30),
 tertiary=Color(0xFFA66A35), tertiaryContainer=Color(0xFFFFEBD6), onTertiaryContainer=Color(0xFF633D1C),
 background=Color(0xFFFBF7F2), onBackground=Color(0xFF392923), surface=Color(0xFFFCF9F5), onSurface=Color(0xFF392923),
 surfaceVariant=Color(0xFFF4E8E1), onSurfaceVariant=Color(0xFF766258), outline=Color(0xFFAB9285), outlineVariant=Color(0xFFE6D8CF),
 surfaceContainerLowest=Color(0xFFFFFCF9), surfaceContainerLow=Color(0xFFFAF1EB), surfaceContainer=Color(0xFFF4E8E1),
 surfaceContainerHigh=Color(0xFFEFDFD5), surfaceContainerHighest=Color(0xFFEBD8CD), surfaceTint=Color(0xFFAF583D),
 error=Color(0xFFB3261E), onError=Color.White, errorContainer=Color(0xFFFCE5E0), onErrorContainer=Color(0xFF7C221B))
private val Type = Typography(
 displaySmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=40.sp,lineHeight=46.sp),
 headlineLarge=TextStyle(fontWeight=FontWeight.Bold,fontSize=32.sp,lineHeight=38.sp),
 headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=34.sp),
 headlineSmall=TextStyle(fontWeight=FontWeight.Bold,fontSize=24.sp,lineHeight=30.sp),
 titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=21.sp,lineHeight=27.sp),
 titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp),
 bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
 labelLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp))
@Composable fun CarCareTheme(content: @Composable () -> Unit) {
 MaterialTheme(colorScheme=Terracotta, typography=Type, shapes=Shapes(
 extraSmall=RoundedCornerShape(10.dp),small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(28.dp)), content=content)
}
