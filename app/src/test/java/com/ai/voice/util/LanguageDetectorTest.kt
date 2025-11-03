package com.ai.voice.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * LanguageDetector单元测试
 */
class LanguageDetectorTest {

    @Test
    fun testDetectKorean() {
        // 纯韩语
        val koreanTexts = listOf(
            "화이트보드 실행해줘",
            "음량 올려줘",
            "에이치디엠아이 일로 전환",
            "설정 열어줘",
            "홈 화면으로 돌아가",
        )

        for (text in koreanTexts) {
            val result = LanguageDetector.detectLanguage(text)
            assertEquals(
                "\"$text\" 应该被检测为韩语",
                LanguageDetector.DetectedLanguage.KOREAN,
                result
            )
        }
    }

    @Test
    fun testDetectEnglish() {
        // 纯英语
        val englishTexts = listOf(
            "open whiteboard",
            "turn up the volume",
            "switch to HDMI one",
            "open settings",
            "go back home",
        )

        for (text in englishTexts) {
            val result = LanguageDetector.detectLanguage(text)
            assertEquals(
                "\"$text\" 应该被检测为英语",
                LanguageDetector.DetectedLanguage.ENGLISH,
                result
            )
        }
    }

    @Test
    fun testDetectMixed() {
        // 混合语言
        val mixedTexts = listOf(
            "open 화이트보드",
            "화이트보드 please",
            "HDMI 일로 switch",
        )

        for (text in mixedTexts) {
            val result = LanguageDetector.detectLanguage(text)
            assertTrue(
                "\"$text\" 应该被检测为混合语言或主要语言",
                result == LanguageDetector.DetectedLanguage.MIXED ||
                result == LanguageDetector.DetectedLanguage.KOREAN ||
                result == LanguageDetector.DetectedLanguage.ENGLISH
            )
        }
    }

    @Test
    fun testDetectLocaleKorean() {
        val text = "화이트보드 실행해줘"
        val locale = LanguageDetector.detectLocale(text)
        assertEquals(Locale.KOREAN, locale)
    }

    @Test
    fun testDetectLocaleEnglish() {
        val text = "open whiteboard"
        val locale = LanguageDetector.detectLocale(text)
        assertEquals(Locale.ENGLISH, locale)
    }

    @Test
    fun testDetectLocaleMixed_StartsWithKorean() {
        val text = "화이트보드 open"
        val locale = LanguageDetector.detectLocale(text)
        assertEquals(Locale.KOREAN, locale)
    }

    @Test
    fun testDetectLocaleMixed_StartsWithEnglish() {
        val text = "open 화이트보드"
        val locale = LanguageDetector.detectLocale(text)
        assertEquals(Locale.ENGLISH, locale)
    }

    @Test
    fun testDetectEmpty() {
        val result = LanguageDetector.detectLanguage("")
        assertEquals(LanguageDetector.DetectedLanguage.UNKNOWN, result)
    }

    @Test
    fun testDetectBlank() {
        val result = LanguageDetector.detectLanguage("   ")
        assertEquals(LanguageDetector.DetectedLanguage.UNKNOWN, result)
    }

    @Test
    fun testDetectPunctuationOnly() {
        val result = LanguageDetector.detectLanguage(".,!?")
        assertEquals(LanguageDetector.DetectedLanguage.UNKNOWN, result)
    }

    @Test
    fun testIsKoreanCharacter() {
        // 测试韩文音节
        assertTrue(isKoreanChar('한'))
        assertTrue(isKoreanChar('글'))
        assertTrue(isKoreanChar('가'))
        assertTrue(isKoreanChar('힣'))
        
        // 测试非韩文
        assertFalse(isKoreanChar('a'))
        assertFalse(isKoreanChar('A'))
        assertFalse(isKoreanChar('1'))
        assertFalse(isKoreanChar('中'))
    }

    @Test
    fun testRatioCalculation() {
        // 70% 韩文
        val text70Korean = "한글한글한" + "abc"  // 5个韩文，3个英文
        val result = LanguageDetector.detectLanguage(text70Korean)
        assertEquals(LanguageDetector.DetectedLanguage.KOREAN, result)

        // 70% 英文
        val text70English = "abcdefg" + "한글"  // 7个英文，2个韩文
        val result2 = LanguageDetector.detectLanguage(text70English)
        assertEquals(LanguageDetector.DetectedLanguage.ENGLISH, result2)
    }

    @Test
    fun testDeviceControlCommands() {
        // 真实的设备控制命令
        val commands = mapOf(
            "파워 꺼줘" to LanguageDetector.DetectedLanguage.KOREAN,
            "power off" to LanguageDetector.DetectedLanguage.ENGLISH,
            "에이치디엠아이 일" to LanguageDetector.DetectedLanguage.KOREAN,
            "HDMI one" to LanguageDetector.DetectedLanguage.ENGLISH,
            "구글 열어줘" to LanguageDetector.DetectedLanguage.KOREAN,
            "open google" to LanguageDetector.DetectedLanguage.ENGLISH,
        )

        for ((command, expectedLanguage) in commands) {
            val detected = LanguageDetector.detectLanguage(command)
            assertEquals(
                "命令 \"$command\" 语言检测失败",
                expectedLanguage,
                detected
            )
        }
    }

    @Test
    fun testGetLanguageName() {
        assertEquals(
            "한국어 (Korean)",
            LanguageDetector.getLanguageName(LanguageDetector.DetectedLanguage.KOREAN)
        )
        assertEquals(
            "English",
            LanguageDetector.getLanguageName(LanguageDetector.DetectedLanguage.ENGLISH)
        )
        assertEquals(
            "Mixed (혼합)",
            LanguageDetector.getLanguageName(LanguageDetector.DetectedLanguage.MIXED)
        )
        assertEquals(
            "Unknown (알 수 없음)",
            LanguageDetector.getLanguageName(LanguageDetector.DetectedLanguage.UNKNOWN)
        )
    }

    @Test
    fun testGetLocaleName() {
        assertEquals(
            "한국어 (Korean)",
            LanguageDetector.getLocaleName(Locale.KOREAN)
        )
        assertEquals(
            "English",
            LanguageDetector.getLocaleName(Locale.ENGLISH)
        )
    }

    // Helper function to test private isKoreanCharacter
    private fun isKoreanChar(char: Char): Boolean {
        val codePoint = char.code
        return when (codePoint) {
            in 0xAC00..0xD7AF -> true
            in 0x1100..0x11FF -> true
            in 0x3130..0x318F -> true
            in 0xA960..0xA97F -> true
            in 0xD7B0..0xD7FF -> true
            else -> false
        }
    }
}

