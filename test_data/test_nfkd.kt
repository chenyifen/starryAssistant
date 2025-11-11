import org.dicio.skill.standard.util.nfkdNormalizeWord

fun main() {
    val input = "입력소스참띄줘"
    val normalized = nfkdNormalizeWord(input)
    
    println("原始输入: '$input'")
    println("NFKD标准化后: '$normalized'")
    println("长度变化: ${input.length} -> ${normalized.length}")
    
    // 检查YAML中的例子
    val yamlExample = "입력소스창띄워줘"
    println("YAML中的例子: '$yamlExample'")
    println("是否匹配: ${normalized == yamlExample}")
    
    // 测试我们的处理流程
    val processed = nfkdNormalizeWord(input.replace(Regex("\\s+"), "").replace(Regex("[.,。，!！?？;；:：]"), ""))
    println("完整处理后: '$processed'")
    println("与YAML匹配: ${processed == yamlExample}")
}
