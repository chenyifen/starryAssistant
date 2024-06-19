package org.stypox.dicio.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.stypox.dicio.R
import org.stypox.dicio.ui.home.SttState.Downloaded
import org.stypox.dicio.ui.home.SttState.Downloading
import org.stypox.dicio.ui.home.SttState.ErrorDownloading
import org.stypox.dicio.ui.home.SttState.ErrorLoading
import org.stypox.dicio.ui.home.SttState.ErrorUnzipping
import org.stypox.dicio.ui.home.SttState.Listening
import org.stypox.dicio.ui.home.SttState.Loaded
import org.stypox.dicio.ui.home.SttState.Loading
import org.stypox.dicio.ui.home.SttState.NoMicrophonePermission
import org.stypox.dicio.ui.home.SttState.NotAvailable
import org.stypox.dicio.ui.home.SttState.NotDownloaded
import org.stypox.dicio.ui.home.SttState.NotInitialized
import org.stypox.dicio.ui.home.SttState.NotLoaded
import org.stypox.dicio.ui.home.SttState.Unzipping
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.LoadingProgress
import org.stypox.dicio.ui.util.SmallCircularProgressIndicator
import org.stypox.dicio.ui.util.SttStatesPreviews
import org.stypox.dicio.ui.util.loadingProgressString

// TODO handle long click to report errors

/**
 * Calls [SttFabImpl] with the data from the view model, and handles the microhone permission.
 */
@Composable
fun SttFab(state: SttState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var microphonePermissionGranted by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(null) {
        microphonePermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        microphonePermissionGranted = isGranted
    }

    // the NoMicrophonePermission state should override any other state, except for the NotAvailable
    // state which indicates that the STT engine can't be made available for this locale
    val useNoMicPermState = !microphonePermissionGranted && state != NotAvailable
    SttFabImpl(
        state = if (useNoMicPermState) NoMicrophonePermission else state,
        onClick = if (useNoMicPermState)
            { -> launcher.launch(Manifest.permission.RECORD_AUDIO) }
        else
            onClick,
        modifier = modifier,
    )
}

/**
 * Renders a multi-use [ExtendedFloatingActionButton] that shows the current Stt state, and allows
 * to perform corresponding actions (downloading/unzipping/loading/listening) when pressed.
 */
@Composable
private fun SttFabImpl(state: SttState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = sttFabText(state)
    var lastNonEmptyText by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text != lastNonEmptyText && text.isNotEmpty()) {
            lastNonEmptyText = text
        }
    }

    ExtendedFloatingActionButton(
        text = {
            Text(
                text = lastNonEmptyText,
                textAlign = TextAlign.Center,
            )
        },
        icon = { SttFabIcon(state, contentDescription = text) },
        onClick = onClick,
        expanded = text.isNotEmpty(),
        modifier = modifier,
    )
}

@Composable
private fun sttFabText(state: SttState): String {
    return when (state) {
        NoMicrophonePermission -> stringResource(R.string.grant_microphone_permission)
        NotInitialized -> ""
        NotAvailable -> stringResource(R.string.stt_not_available)
        NotDownloaded -> stringResource(R.string.stt_download)
        is Downloading -> loadingProgressString(
            LocalContext.current,
            state.currentBytes,
            state.totalBytes,
        )
        is ErrorDownloading -> stringResource(R.string.error_downloading)
        Downloaded -> stringResource(R.string.stt_unzip)
        is Unzipping -> stringResource(R.string.unzipping)
        is ErrorUnzipping -> stringResource(R.string.error_unzipping)
        NotLoaded -> ""
        is Loading -> ""
        is ErrorLoading -> stringResource(R.string.error_loading)
        is Loaded -> ""
        is Listening -> stringResource(R.string.listening)
    }
}

@Composable
private fun SttFabIcon(state: SttState, contentDescription: String) {
    when (state) {
        NoMicrophonePermission -> Icon(Icons.Default.Warning, contentDescription)
        NotInitialized -> SmallCircularProgressIndicator()
        NotAvailable -> Icon(Icons.Default.Warning, contentDescription)
        NotDownloaded -> Icon(Icons.Default.Download, contentDescription)
        is Downloading -> LoadingProgress(state.currentBytes, state.totalBytes)
        is ErrorDownloading -> Icon(Icons.Default.Error, contentDescription)
        Downloaded -> Icon(Icons.Default.FolderZip, contentDescription)
        is Unzipping -> LoadingProgress(state.currentBytes, state.totalBytes)
        is ErrorUnzipping -> Icon(Icons.Default.Error, contentDescription)
        NotLoaded -> Icon(Icons.Default.MicNone, stringResource(R.string.start_listening))
        is Loading -> if (state.thenStartListening)
            SmallCircularProgressIndicator()
        else // show the microphone if the model is loading but is not going to listen
            Icon(Icons.Default.MicNone, stringResource(R.string.start_listening))
        is ErrorLoading -> Icon(Icons.Default.Error, contentDescription)
        is Loaded -> Icon(Icons.Default.MicNone, stringResource(R.string.start_listening))
        is Listening -> Icon(Icons.Default.Mic, contentDescription)
    }
}

@Preview
@Composable
private fun SttFabPreview(@PreviewParameter(SttStatesPreviews::class) state: SttState) {
    Column {
        Text(
            text = state.toString(),
            maxLines = 1,
            fontSize = 9.sp,
            color = Color.Black,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .background(Color.White.copy(alpha = 0.5f))
                .width(256.dp)
        )
        SttFabImpl(
            state = state,
            onClick = {},
        )
    }
}

// this preview is useful to take screenshots
@Preview(device = "spec:width=2500px,height=2340px,dpi=440")
@Composable
private fun SttFabPreviewAll() {
    AppTheme {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (state in SttStatesPreviews().values) {
                if ((state is Downloading && state.totalBytes == 0L) ||
                    (state is Unzipping && state.totalBytes == 0L)) {
                    continue // not useful in screenshots
                }
                SttFabImpl(
                    state = state,
                    onClick = {},
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
