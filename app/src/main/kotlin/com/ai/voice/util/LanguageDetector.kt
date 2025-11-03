package com.ai.voice.util

import java.util.Locale

/**
 * 语言检测器
 * 
 * 检测文本是韩语、英语还是混合语言
 */
object LanguageDetector {
    
    /**
     * 支持的语言类型
     */
    enum class DetectedLanguage {
        KOREAN,
        ENGLISH,
        MIXED,    // 混合语言
        UNKNOWN   // 无法判断
    }
    
    /**
     * 检测文本的主要语言
     * 
     * 检测逻辑：
     * 1. 统计韩文、英文、数字、标点等字符数量
     * 2. 根据比例判断主要语言
     * 3. 如果两种语言都占显著比例，返回MIXED
     * 
     * @param text 要检测的文本
     * @return 检测到的语言类型
     */
    fun detectLanguage(text: String): DetectedLanguage {
        if (text.isBlank()) {
            return DetectedLanguage.UNKNOWN
        }
        
        var koreanCount = 0
        var englishCount = 0
        var otherCount = 0
        
        for (char in text) {
            when {
                isKoreanCharacter(char) -> koreanCount++
                isEnglishCharacter(char) -> englishCount++
                char.isWhitespace() || isPunctuation(char) -> {
                    // 忽略空格和标点符号
                }
                else -> otherCount++
            }
        }
        
        val totalCount = koreanCount + englishCount + otherCount
        if (totalCount == 0) {
            return DetectedLanguage.UNKNOWN
        }
        
        val koreanRatio = koreanCount.toFloat() / totalCount
        val englishRatio = englishCount.toFloat() / totalCount
        
        // 判断逻辑：
        // - 如果某种语言占比 >= 70%，判定为该语言
        // - 如果两种语言都占显著比例（都 >= 20%），判定为混合
        // - 否则选择占比较高的语言
        return when {
            koreanRatio >= 0.7f -> DetectedLanguage.KOREAN
            englishRatio >= 0.7f -> DetectedLanguage.ENGLISH
            koreanRatio >= 0.2f && englishRatio >= 0.2f -> DetectedLanguage.MIXED
            koreanRatio > englishRatio -> DetectedLanguage.KOREAN
            englishRatio > koreanRatio -> DetectedLanguage.ENGLISH
            else -> DetectedLanguage.UNKNOWN
        }
    }
    
    /**
     * 检测文本语言并返回对应的Locale
     * 
     * @param text 要检测的文本
     * @param defaultLocale 默认语言（当无法检测时使用），默认为韩语
     * @return 对应的Locale对象
     */
    fun detectLocale(text: String, defaultLocale: Locale = Locale.KOREAN): Locale {
        return when (detectLanguage(text)) {
            DetectedLanguage.KOREAN -> Locale.KOREAN
            DetectedLanguage.ENGLISH -> Locale.ENGLISH
            DetectedLanguage.MIXED -> {
                // 混合语言时，优先使用韩语（因为这是主要使用场景）
                // 或者可以根据第一个非标点字符来判断
                val firstChar = text.firstOrNull { !it.isWhitespace() && !isPunctuation(it) }
                if (firstChar != null && isKoreanCharacter(firstChar)) {
                    Locale.KOREAN
                } else if (firstChar != null && isEnglishCharacter(firstChar)) {
                    Locale.ENGLISH
                } else {
                    defaultLocale
                }
            }
            DetectedLanguage.UNKNOWN -> defaultLocale
        }
    }
    
    /**
     * 判断字符是否为韩文字符
     * 
     * 韩文Unicode范围：
     * - AC00-D7AF: 韩文音节（한글 음절）
     * - 1100-11FF: 韩文字母（한글 자모）
     * - 3130-318F: 韩文兼容字母（한글 호환 자모）
     * - A960-A97F: 韩文扩展字母A
     * - D7B0-D7FF: 韩文扩展字母B
     */
    private fun isKoreanCharacter(char: Char): Boolean {
        val codePoint = char.code
        return when (codePoint) {
            in 0xAC00..0xD7AF -> true  // 韩文音节
            in 0x1100..0x11FF -> true  // 韩文字母
            in 0x3130..0x318F -> true  // 韩文兼容字母
            in 0xA960..0xA97F -> true  // 韩文扩展A
            in 0xD7B0..0xD7FF -> true  // 韩文扩展B
            else -> false
        }
    }
    
    /**
     * 判断字符是否为英文字符
     */
    private fun isEnglishCharacter(char: Char): Boolean {
        return char in 'A'..'Z' || char in 'a'..'z'
    }
    
    /**
     * 判断字符是否为标点符号
     */
    private fun isPunctuation(char: Char): Boolean {
        return when (char) {
            '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}' -> true
            '\u3002', '\uFF0C', '\uFF01', '\uFF1F', '\uFF1B', '\uFF1A' -> true  // 中文标点
            '\u201C', '\u201D', '\u2018', '\u2019' -> true  // 智能引号
            '\uFF08', '\uFF09', '\u3010', '\u3011' -> true  // 全角括号
            else -> false
        }
    }
    
    /**
     * 获取语言的友好名称（用于日志）
     */
    fun getLanguageName(language: DetectedLanguage): String {
        return when (language) {
            DetectedLanguage.KOREAN -> "한국어 (Korean)"
            DetectedLanguage.ENGLISH -> "English"
            DetectedLanguage.MIXED -> "Mixed (혼합)"
            DetectedLanguage.UNKNOWN -> "Unknown (알 수 없음)"
        }
    }
    
    /**
     * 获取Locale的友好名称（用于日志）
     */
    fun getLocaleName(locale: Locale): String {
        return when (locale.language) {
            "ko" -> "한국어 (Korean)"
            "en" -> "English"
            else -> locale.displayName
        }
    }
}

