package com.umain.fortress.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.PrimaryButton
import com.umain.fortress.ui.theme.Midnight700
import com.umain.fortress.ui.theme.Midnight800
import com.umain.fortress.ui.theme.Midnight900
import com.umain.fortress.ui.theme.Violet500
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private data class Slide(val icon: ImageVector, val title: String, val body: String)

private val slides = listOf(
    Slide(
        icon = Icons.Default.Lock,
        title = "Hardware-backed by default",
        body = "Your session lives behind the Android Keystore — a hardware vault that signs and decrypts on your behalf. Tokens never leave the TEE in clear.",
    ),
    Slide(
        icon = Icons.Default.Fingerprint,
        title = "Passwordless future",
        body = "Strong biometrics authorise each sensitive action. Each transfer is signed inside a CryptoObject — the bytes can only exist if you authorised them.",
    ),
    Slide(
        icon = Icons.Default.VerifiedUser,
        title = "Zero-trust by design",
        body = "Every device is scored, every action is verified, every signal is weighed. Fortress assumes the network is hostile and trusts only what it can prove.",
    ),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val pagerState = rememberPagerState(initialPage = 0) { slides.size }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Midnight900, Midnight800, Violet500.copy(alpha = 0.55f))),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { viewModel.finish(onFinished) }) {
                    Text(
                        text = "Skip",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 32.dp),
            ) { page ->
                SlideCard(slides[page])
            }

            DotIndicator(
                count = slides.size,
                selected = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 24.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            PrimaryButton(
                text = if (pagerState.currentPage == slides.lastIndex) "Get started" else "Next",
                onClick = {
                    if (pagerState.currentPage == slides.lastIndex) {
                        viewModel.finish(onFinished)
                    } else {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Suppress unused-import nag — palette is referenced via gradient above.
        @Suppress("UNUSED_EXPRESSION") Midnight700
    }
}

@Composable
private fun SlideCard(slide: Slide) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f),
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = slide.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun DotIndicator(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val activeColor = MaterialTheme.colorScheme.onPrimary
        val inactiveColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
        repeat(count) { index ->
            val isActive = index == selected
            Canvas(modifier = Modifier.size(if (isActive) 10.dp else 8.dp)) {
                drawCircle(color = if (isActive) activeColor else inactiveColor)
            }
        }
    }
}
