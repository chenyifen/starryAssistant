package com.ai.voice.skills.lyrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.R
import com.ai.voice.io.graphical.Body
import com.ai.voice.io.graphical.Headline
import com.ai.voice.io.graphical.HeadlineSpeechSkillOutput
import com.ai.voice.io.graphical.Subtitle
import com.ai.voice.util.getString

sealed interface LyricsOutput : SkillOutput {
    data class Success(
        val title: String,
        val artist: String,
        val lyrics: String,
    ) : LyricsOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
            R.string.skill_lyrics_found_song_by_artist, title, artist
        )

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Headline(text = title)
                Subtitle(text = artist)
                Spacer(modifier = Modifier.height(12.dp))
                Body(text = lyrics)
            }
        }
    }

    data class Failed(
        val title: String,
    ) : HeadlineSpeechSkillOutput, LyricsOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
            R.string.skill_lyrics_song_not_found, title
        )
    }
}
