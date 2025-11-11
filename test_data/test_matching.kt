import org.dicio.skill.standard.util.nfkdNormalizeWord

fun main() {
    // 测试我们当前的输入
    val input = "하면 캡쳐 해죠."
    val processed = nfkdNormalizeWord(input.replace(Regex("\\s+"), "").replace(Regex("[.,。，!！?？;；:：]"), ""))
    
    println("原始输入: '$input'")
    println("处理后: '$processed'")
    
    // 检查一些现有的句子是否匹配
    val existingSentences = listOf(
        "스크린샷찍기",
        "화면캡처",
        "화면캡쳐해줘",
        "하면캡쳐해줘"  // 我们新添加的
    )
    
    existingSentences.forEach { sentence ->
        val normalized = nfkdNormalizeWord(sentence)
        val matches = processed == normalized
        println("句子 '$sentence' -> '$normalized' 匹配: $matches")
    }
}
