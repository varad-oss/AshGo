package com.varad.unstoppableash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.PremiumBackground
import com.varad.unstoppableash.ui.theme.TextSecondary
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    
    val photoResIds = listOf(
        R.drawable.photo_1,
        R.drawable.photo_2,
        R.drawable.photo_3,
        R.drawable.photo_4,
        R.drawable.photo_5,
        R.drawable.photo_6,
        R.drawable.photo_7,
        R.drawable.photo_8,
        R.drawable.photo_9
    )
    val placeholderColors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFFD54F), Color(0xFF90A4AE), Color(0xFFF06292))

    Box(modifier = Modifier.fillMaxSize().background(PremiumBackground)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("About Us", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                // Top Chronicle Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "T",
                            color = Color.White,
                            fontSize = 64.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 64.sp,
                            modifier = Modifier.padding(end = 4.dp).offset(y = (-6).dp)
                        )
                        
                        Text(
                            text = "he chronicle of Aashika-Varad began in the year 2021, with violent battles in hindi lectures, that transformed ",
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Start,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                    
                    val annotatedText = buildAnnotatedString {
                        append("into... ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Read more.")
                        }
                    }
                    
                    Text(
                        text = annotatedText,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.offset(y = (-4).dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Photo Ropes Section
                val dipDp = 60.dp
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Draw ropes
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val ropeColor = Color.DarkGray
                        val stroke = Stroke(width = 4f)
                        
                        // Shifted ropes up by modifying yOffsets
                        val yOffsets = listOf(h * 0.10f, h * 0.40f, h * 0.70f)
                        val dipPx = dipDp.toPx()
                        
                        yOffsets.forEach { y ->
                            val path = Path().apply {
                                moveTo(0f, y)
                                quadraticTo(w / 2, y + dipPx, w, y)
                            }
                            drawPath(path, color = ropeColor, style = stroke)
                        }
                    }
                    
                    // Position thumbnails
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val w = maxWidth.value
                        val h = maxHeight.value
                        
                        val yOffsets = listOf(h * 0.10f, h * 0.40f, h * 0.70f)
                        val dipValue = dipDp.value
                        
                        fun getCurveY(t: Float, startY: Float): Float {
                            return ((1 - t) * (1 - t) * startY + 2 * (1 - t) * t * (startY + dipValue) + t * t * startY)
                        }
                        
                        val photoWidth = 60.dp
                        val photoHeight = 70.dp
                        val tValues = listOf(0.15f, 0.5f, 0.85f)
                        
                        photoResIds.forEachIndexed { index, _ ->
                            val ropeIndex = index / 3
                            if (ropeIndex < 3) {
                                val tIndex = index % 3
                                val t = tValues[tIndex]
                                
                                val startY = yOffsets[ropeIndex]
                                val xPos = w * t - (photoWidth.value / 2)
                                val yPos = getCurveY(t, startY)
                                
                                val rotation = remember { (-15..15).random().toFloat() }
                                
                                Box(
                                    modifier = Modifier
                                        .offset(x = xPos.dp, y = yPos.dp)
                                        .size(photoWidth, photoHeight)
                                        .graphicsLayer { rotationZ = rotation }
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(placeholderColors[index % placeholderColors.size])
                                        .clickable { selectedPhotoIndex = index }
                                ) {
                                    photoResIds[index]?.let { resId ->
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = "Photo $index",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Text(
                    text = "Made with ❤️, by Varad, for his ❤️",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp)
                )
            }
        }
        
        // Full Screen Photo Viewer Overlay
        AnimatedVisibility(
            visible = selectedPhotoIndex != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                selectedPhotoIndex?.let { index ->
                    // Full screen photo placeholder
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .aspectRatio(3f/4f)
                            .padding(32.dp)
                            .background(if (photoResIds[index] != null) Color.Transparent else placeholderColors[index % placeholderColors.size])
                    ) {
                        photoResIds[index]?.let { resId ->
                            Image(
                                painter = painterResource(id = resId),
                                contentDescription = "Photo full",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = { selectedPhotoIndex = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 16.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
