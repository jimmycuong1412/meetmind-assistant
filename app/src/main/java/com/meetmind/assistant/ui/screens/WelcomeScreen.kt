package com.meetmind.assistant.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.components.GradientButton
import com.meetmind.assistant.ui.components.ResponsiveContent
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.*

/**
 * Welcome screen — full-screen immersive gradient.
 *
 * Layout (single surface, no hero/sheet split):
 *  - App logo + name + subtitle at top
 *  - Flexible space
 *  - Subtle divider
 *  - Three feature rows (Privacy first) with white icons and text
 *  - Flexible space
 *  - "Get Started" white button
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DownloadImmersiveGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ResponsiveContent {
            Spacer(modifier = Modifier.height(48.dp))

            // ── Logo + name ──────────────────────────────────────────────
            Image(
                painter = painterResource(id = R.drawable.meetmind_logo),
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.app_name),
                fontSize = 30.sp,
                fontFamily = SpaceGroteskFont,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.semanticColors.onGradient
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.onGradientVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Divider ──────────────────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.semanticColors.onGradientDivider)

            Spacer(modifier = Modifier.height(36.dp))

            // ── Feature rows — Privacy first ────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                ImmersiveFeatureRow(
                    icon = AppIcons.Lock,
                    title = stringResource(R.string.welcome_feature_privacy_title),
                    description = stringResource(R.string.welcome_feature_privacy_desc)
                )

                ImmersiveFeatureRow(
                    icon = AppIcons.Mic,
                    title = stringResource(R.string.welcome_feature_transcription_title),
                    description = stringResource(R.string.welcome_feature_transcription_desc)
                )

                ImmersiveFeatureRow(
                    icon = AppIcons.AutoAwesome,
                    title = stringResource(R.string.welcome_feature_ai_title),
                    description = stringResource(R.string.welcome_feature_ai_desc)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── CTA — white button on dark gradient ──────────────────────
            GradientButton(
                text = stringResource(R.string.welcome_get_started),
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
                gradient = SolidColor(MaterialTheme.semanticColors.onGradient),
                textColor = GradientTop
            )

            Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Feature row styled for a dark/gradient background:
 * white icon circle + white title and description.
 */
@Composable
private fun ImmersiveFeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.semanticColors.onGradientScrim,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.onGradient,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.semanticColors.onGradient
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.semanticColors.onGradientVariant
            )
        }
    }
}
