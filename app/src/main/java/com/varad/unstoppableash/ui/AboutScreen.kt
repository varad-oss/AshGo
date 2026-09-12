package com.varad.unstoppableash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varad.unstoppableash.ui.theme.PremiumBackground
import com.varad.unstoppableash.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Us", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PremiumBackground)
            )
        },
        containerColor = PremiumBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "T",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 72.sp,
                        modifier = Modifier.padding(end = 4.dp).offset(y = (-6).dp)
                    )
                    
                    Text(
                        text = "he chronicle of Aashika-Varad began in the year 2021, with violent battles in hindi lectures, that transformed ",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 26.sp,
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
                    fontSize = 18.sp,
                    textAlign = TextAlign.Start,
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.offset(y = (-4).dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Made with ❤️, by Varad, for his ❤️",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }
    }
}
