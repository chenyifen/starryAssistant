package com.ai.voice.ui.util

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.io.input.SttState
import com.ai.voice.io.wake.WakeState
import com.ai.voice.skills.fallback.text.TextFallbackOutput
import com.ai.voice.skills.power_control.PowerControlInfo
import com.ai.voice.skills.input_source.InputSourceControlInfo
import com.ai.voice.skills.app_launcher.AppLauncherInfo
import com.ai.voice.skills.whiteboard_tools.WhiteboardToolsInfo
import com.ai.voice.skills.system_navigation.SystemNavigationInfo
import com.ai.voice.ui.home.Interaction
import com.ai.voice.ui.home.InteractionLog
import com.ai.voice.ui.home.PendingQuestion
import com.ai.voice.ui.home.QuestionAnswer
import java.io.IOException


class UserInputPreviews : CollectionPreviewParameterProvider<String>(listOf(
    "",
    "When",
    "What's the weather?",
    LoremIpsum(50).values.first(),
))

class SkillInfoPreviews : CollectionPreviewParameterProvider<SkillInfo>(listOf(
    PowerControlInfo,
    InputSourceControlInfo,
    AppLauncherInfo,
    WhiteboardToolsInfo,
    SystemNavigationInfo,
    object : SkillInfo("test") {
        override fun name(context: Context) = "Long name lorem ipsum dolor sit amet, consectetur"
        override fun sentenceExample(context: Context) = "Long sentence ".repeat(20)
        @Composable override fun icon() = rememberVectorPainter(Icons.Default.Extension)
        override fun isAvailable(ctx: SkillContext) = true
        override fun build(ctx: SkillContext) = error("not-implemented preview-only")
    },
))

class SkillOutputPreviews : CollectionPreviewParameterProvider<SkillOutput>(listOf(
    TextFallbackOutput(askToRepeat = true),
))

class InteractionLogPreviews : CollectionPreviewParameterProvider<InteractionLog>(listOf(
    InteractionLog(
        listOf(),
        null,
    ),
    InteractionLog(
        listOf(),
        PendingQuestion(
            userInput = "What's the weather?",
            continuesLastInteraction = true,
            skillBeingEvaluated = null,
        ),
    ),
    InteractionLog(
        listOf(),
        PendingQuestion(
            userInput = LoremIpsum(50).values.first(),
            continuesLastInteraction = false,
            skillBeingEvaluated = SkillInfoPreviews().values.first(),
        ),
    ),
    InteractionLog(
        listOf(
            Interaction(
                skill = PowerControlInfo,
                questionsAnswers = listOf(
                    QuestionAnswer("전원 켜줘", TextFallbackOutput(askToRepeat = false)),
                )
            )
        ),
        PendingQuestion(
            userInput = "화이트보드 열기",
            continuesLastInteraction = false,
            skillBeingEvaluated = WhiteboardToolsInfo,
        ),
    ),
))

class SttStatesPreviews : CollectionPreviewParameterProvider<SttState>(listOf(
    SttState.NoMicrophonePermission,
    SttState.NotInitialized,
    SttState.NotAvailable,
    SttState.NotDownloaded,
    SttState.Downloading(Progress(0, 3, 987654, 0)),
    SttState.Downloading(Progress(5, 0, 987654, 0)),
    SttState.Downloading(Progress(0, 1, 987654, 1234567)),
    SttState.ErrorDownloading(IOException("ErrorDownloading exception")),
    SttState.Downloaded,
    SttState.Unzipping(Progress(0, 0, 765432, 0)),
    SttState.Unzipping(Progress(2, 3, 3365432, 9876543)),
    SttState.ErrorUnzipping(Exception("ErrorUnzipping exception")),
    SttState.NotLoaded,
    SttState.Loading(true),
    SttState.Loading(false),
    SttState.ErrorLoading(Exception("ErrorLoading exception")),
    SttState.Loaded,
    SttState.Listening,
    SttState.WaitingForResult,
))

class WakeStatesPreviews : CollectionPreviewParameterProvider<WakeState>(listOf(
    WakeState.NotDownloaded,
    WakeState.Downloading(Progress(0, 0, 987654, 0)),
    WakeState.Downloading(Progress(1, 2, 987654, 1234567)),
    WakeState.ErrorDownloading(Exception("ErrorDownloading exception")),
    WakeState.Loading,
    WakeState.ErrorLoading(Exception("ErrorLoading exception")),
    WakeState.Loaded,
))
