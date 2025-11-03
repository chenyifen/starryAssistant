package com.ai.voice.util

/**
 * ASR文本标准化工具
 * 
 * 将ASR识别出的文本转换为可以被句子编译器匹配的格式。
 * 主要功能：
 * 1. 将阿拉伯数字转换为韩语文字
 * 2. 标准化常见的技术术语
 * 
 * 原因：dicio-sentences-compiler不支持在命令中使用阿拉伯数字，
 * 但SenseVoice ASR模型经常会输出带数字的文本。
 */
object AsrTextNormalizer {
    
    /**
     * 标准化ASR输出文本，使其可以被命令匹配器识别
     */
    fun normalize(asrText: String): String {
        var normalized = asrText
        
        // 1. HDMI相关命令
        normalized = normalizeHdmiCommands(normalized)
        
        // 2. USB-C相关命令
        normalized = normalizeUsbCommands(normalized)
        
        // 3. Play Store相关
        normalized = normalizePlayStoreCommands(normalized)
        
        // 4. 其他常见数字
        normalized = normalizeCommonNumbers(normalized)
        
        return normalized
    }
    
    /**
     * 标准化HDMI相关命令
     * 例如: "에치 데마1" -> "에치 데마일"
     */
    private fun normalizeHdmiCommands(text: String): String {
        var result = text
        
        // HDMI 1 相关
        result = result.replace(Regex("(?i)hdmi\\s*1"), "hdmi 일")
        result = result.replace(Regex("(?i)h\\s*d\\s*m\\s*i\\s*1"), "hdmi 일")
        result = result.replace(Regex("에치.*?디.*?[엠엠마].*?[아이].*?1")) { matchResult ->
            matchResult.value.replace("1", "일")
        }
        result = result.replace(Regex("에이치.*?디.*?[엠엠마].*?[아이].*?1")) { matchResult ->
            matchResult.value.replace("1", "일")
        }
        
        // HDMI 2 相关
        result = result.replace(Regex("(?i)hdmi\\s*2"), "hdmi 이")
        result = result.replace(Regex("(?i)h\\s*d\\s*m\\s*i\\s*2"), "hdmi 이")
        result = result.replace(Regex("에치.*?디.*?[엠엠마].*?[아이].*?2")) { matchResult ->
            matchResult.value.replace("2", "이")
        }
        result = result.replace(Regex("에이치.*?디.*?[엠엠마].*?[아이].*?2")) { matchResult ->
            matchResult.value.replace("2", "이")
        }
        
        // 特殊模式: M2, DM2等
        result = result.replace(Regex("(?i)M2"), "엠투")
        result = result.replace(Regex("(?i)DM2"), "디엠투")
        result = result.replace(Regex("(?i)H\\s*DM2"), "에이치 디엠투")
        
        return result
    }
    
    /**
     * 标准化USB-C相关命令
     */
    private fun normalizeUsbCommands(text: String): String {
        var result = text
        
        // USB-C with numbers
        result = result.replace(Regex("(?i)usb\\s*c?\\s*[0-9]+")) { matchResult ->
            // 移除数字
            matchResult.value.replace(Regex("[0-9]+"), "")
        }
        
        return result
    }
    
    /**
     * 标准化Play Store相关命令
     */
    private fun normalizePlayStoreCommands(text: String): String {
        var result = text
        
        // 플레이2 -> 플레이 투
        result = result.replace(Regex("플레이\\s*2"), "플레이 투")
        result = result.replace(Regex("플레이\\s*투\\s*2"), "플레이 투")
        
        return result
    }
    
    /**
     * 标准化其他常见数字表达
     */
    private fun normalizeCommonNumbers(text: String): String {
        var result = text
        
        // 将独立的阿拉伯数字转换为韩语
        // 注意：只转换被空格或标点包围的数字，避免误转换
        val numberMap = mapOf(
            "0" to "영",
            "1" to "일",
            "2" to "이",
            "3" to "삼",
            "4" to "사",
            "5" to "오",
            "6" to "육",
            "7" to "칠",
            "8" to "팔",
            "9" to "구"
        )
        
        // 转换单独出现的数字（前后有空格或开头/结尾）
        for ((digit, korean) in numberMap) {
            result = result.replace(Regex("(^|\\s)$digit(\\s|$)"), "$1$korean$2")
        }
        
        // 处理一些特殊情况：末尾的数字（如 "구글0"）
        // 如果数字在词尾且看起来是错误识别，就移除它
        result = result.replace(Regex("구글0"), "구글")
        
        return result
    }
    
    /**
     * 同时返回原始文本和标准化文本，用于双重匹配
     */
    fun getNormalizedVariants(asrText: String): List<String> {
        val normalized = normalize(asrText)
        return if (normalized == asrText) {
            listOf(asrText)
        } else {
            listOf(asrText, normalized) // 先尝试原始，再尝试标准化
        }
    }
}

