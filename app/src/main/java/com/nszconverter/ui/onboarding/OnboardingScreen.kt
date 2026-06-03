package com.nszconverter.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nszconverter.R
import com.nszconverter.domain.model.KeysStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pickKeys = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onKeysSelected(uri)
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 16.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = Icons.Outlined.SettingsSuggest,
                    title = stringResource(R.string.onboarding_title_1),
                    body = stringResource(R.string.onboarding_body_1),
                    extra = {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.onboarding_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    },
                )
                1 -> OnboardingPage(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.onboarding_title_2),
                    body = stringResource(R.string.onboarding_body_2),
                )
                2 -> OnboardingPage(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.onboarding_title_3),
                    body = stringResource(R.string.onboarding_body_3),
                    extra = {
                        Spacer(Modifier.height(24.dp))
                        if (importing) {
                            CircularProgressIndicator()
                        } else {
                            Button(onClick = { pickKeys.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.onboarding_select_keys))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        KeysStatusLine(keys)
                    },
                )
            }
        }

        PagerIndicator(pagerState.currentPage, 3)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text(stringResource(R.string.onboarding_back))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (pagerState.currentPage < 2) {
                Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Text(stringResource(R.string.onboarding_next))
                }
            } else {
                Button(
                    onClick = { viewModel.finishOnboarding(onFinished) },
                    enabled = keys is KeysStatus.Valid,
                ) {
                    Text(stringResource(R.string.onboarding_finish))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    body: String,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        extra()
    }
}

@Composable
private fun PagerIndicator(currentPage: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            val active = i == currentPage
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
            )
        }
    }
}

@Composable
private fun KeysStatusLine(status: KeysStatus) {
    val text = when (status) {
        is KeysStatus.Valid -> "✓ ${status.keyCount} claves cargadas"
        is KeysStatus.Invalid -> "✗ Archivo inválido: ${status.rawError}"
        KeysStatus.NotConfigured -> ""
    }
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
