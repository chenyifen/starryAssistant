import java.util.*

fun detectLocale(text: String, defaultLocale: Locale = Locale.KOREAN): Locale {
    // 简单的语言检测逻辑
    return when {
        text.contains(Regex("[가-힣]")) -> Locale.KOREAN
        text.contains(Regex("[a-zA-Z]")) -> Locale.ENGLISH
        else -> defaultLocale
    }
}

fun processText(text: String): String {
    val locale = detectLocale(text)
    return if (locale.language == "ko") {
        text.replace(Regex("\\s+"), "").replace(Regex("[.,。，!！?？;；:：]"), "")
    } else {
        text
    }
}

fun main() {
    val testCases = listOf(
        "입력소스참띄줘",
        "입력 소스 창 띄워줘",
        "hello world",
        "turn on the light"
    )
    
    testCases.forEach { input ->
        val processed = processText(input)
        val locale = detectLocale(input)
        println("输入: '$input' -> 处理后: '$processed' (语言: ${locale.language})")
    }
}
