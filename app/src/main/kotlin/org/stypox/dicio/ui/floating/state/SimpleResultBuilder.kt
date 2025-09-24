package org.stypox.dicio.ui.floating.state

/**
 * 简单结果构建器
 * 
 * 提供便捷的方法来创建各种类型的SimpleResult实例，
 * 简化技能结果的创建过程
 */
object SimpleResultBuilder {
    
    /**
     * 天气查询结果
     */
    fun weather(location: String, temperature: Int, condition: String): SimpleResult {
        return SimpleResult(
            title = location,
            content = "${temperature}°C, $condition",
            type = ResultType.INFO,
            success = true,
            data = mapOf(
                "location" to location,
                "temperature" to temperature.toString(),
                "condition" to condition,
                "type" to "weather"
            )
        )
    }
    
    /**
     * 应用操作结果
     */
    fun appAction(appName: String, action: String, success: Boolean): SimpleResult {
        return SimpleResult(
            title = if (success) "操作成功" else "操作失败",
            content = "${action} $appName",
            type = ResultType.ACTION,
            success = success,
            data = mapOf(
                "app" to appName,
                "action" to action,
                "type" to "app_operation"
            )
        )
    }
    
    /**
     * 计算结果
     */
    fun calculation(expression: String, result: String): SimpleResult {
        return SimpleResult(
            title = "计算结果",
            content = "$expression = $result",
            type = ResultType.CALC,
            success = true,
            data = mapOf(
                "expression" to expression,
                "result" to result,
                "type" to "calculation"
            )
        )
    }
    
    /**
     * 新闻查询结果
     */
    fun news(headline: String, source: String, summary: String = ""): SimpleResult {
        return SimpleResult(
            title = headline,
            content = if (summary.isNotBlank()) summary else "来源：$source",
            type = ResultType.INFO,
            success = true,
            data = mapOf(
                "headline" to headline,
                "source" to source,
                "summary" to summary,
                "type" to "news"
            )
        )
    }
    
    /**
     * 时间查询结果
     */
    fun time(currentTime: String, timezone: String = ""): SimpleResult {
        return SimpleResult(
            title = "当前时间",
            content = if (timezone.isNotBlank()) "$currentTime ($timezone)" else currentTime,
            type = ResultType.INFO,
            success = true,
            data = mapOf(
                "time" to currentTime,
                "timezone" to timezone,
                "type" to "time"
            )
        )
    }
    
    /**
     * 音乐控制结果
     */
    fun musicControl(action: String, songTitle: String = "", success: Boolean = true): SimpleResult {
        val content = when (action) {
            "play" -> if (songTitle.isNotBlank()) "正在播放：$songTitle" else "开始播放"
            "pause" -> "已暂停"
            "stop" -> "已停止"
            "next" -> "下一首"
            "previous" -> "上一首"
            else -> action
        }
        
        return SimpleResult(
            title = "音乐控制",
            content = content,
            type = ResultType.ACTION,
            success = success,
            data = mapOf(
                "action" to action,
                "song" to songTitle,
                "type" to "music_control"
            )
        )
    }
    
    /**
     * 设备控制结果
     */
    fun deviceControl(deviceName: String, action: String, success: Boolean): SimpleResult {
        return SimpleResult(
            title = if (success) "设备控制成功" else "设备控制失败",
            content = "${action} $deviceName",
            type = ResultType.ACTION,
            success = success,
            data = mapOf(
                "device" to deviceName,
                "action" to action,
                "type" to "device_control"
            )
        )
    }
    
    /**
     * 搜索结果
     */
    fun search(query: String, resultCount: Int, topResult: String = ""): SimpleResult {
        val content = if (topResult.isNotBlank()) {
            "找到 $resultCount 个结果：$topResult"
        } else {
            "找到 $resultCount 个相关结果"
        }
        
        return SimpleResult(
            title = "搜索：$query",
            content = content,
            type = ResultType.INFO,
            success = resultCount > 0,
            data = mapOf(
                "query" to query,
                "count" to resultCount.toString(),
                "top_result" to topResult,
                "type" to "search"
            )
        )
    }
    
    /**
     * 提醒设置结果
     */
    fun reminder(title: String, time: String, success: Boolean): SimpleResult {
        return SimpleResult(
            title = if (success) "提醒已设置" else "提醒设置失败",
            content = "$title - $time",
            type = ResultType.ACTION,
            success = success,
            data = mapOf(
                "title" to title,
                "time" to time,
                "type" to "reminder"
            )
        )
    }
    
    /**
     * 错误结果
     */
    fun error(message: String, errorType: String = "unknown"): SimpleResult {
        return SimpleResult(
            title = "错误",
            content = message,
            type = ResultType.ERROR,
            success = false,
            data = mapOf(
                "error_message" to message,
                "error_type" to errorType,
                "type" to "error"
            )
        )
    }
    
    /**
     * 通用信息结果
     */
    fun info(title: String, content: String, additionalData: Map<String, String> = emptyMap()): SimpleResult {
        return SimpleResult(
            title = title,
            content = content,
            type = ResultType.INFO,
            success = true,
            data = additionalData + mapOf("type" to "info")
        )
    }
    
    /**
     * 通用成功结果
     */
    fun success(title: String, content: String, additionalData: Map<String, String> = emptyMap()): SimpleResult {
        return SimpleResult(
            title = title,
            content = content,
            type = ResultType.ACTION,
            success = true,
            data = additionalData + mapOf("type" to "success")
        )
    }
    
    /**
     * 通用失败结果
     */
    fun failure(title: String, content: String, additionalData: Map<String, String> = emptyMap()): SimpleResult {
        return SimpleResult(
            title = title,
            content = content,
            type = ResultType.ERROR,
            success = false,
            data = additionalData + mapOf("type" to "failure")
        )
    }
    
    /**
     * 从现有技能输出创建结果
     */
    fun fromSkillOutput(skillName: String, outputText: String, success: Boolean = true): SimpleResult {
        return SimpleResult(
            title = skillName,
            content = outputText,
            type = if (success) ResultType.INFO else ResultType.ERROR,
            success = success,
            data = mapOf(
                "skill_name" to skillName,
                "output_text" to outputText,
                "type" to "skill_output"
            )
        )
    }
}
