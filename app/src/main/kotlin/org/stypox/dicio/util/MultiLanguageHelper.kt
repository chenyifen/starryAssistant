package org.stypox.dicio.util

import android.util.Log
import org.dicio.skill.standard.StandardRecognizerData

/**
 * 多语言技能支持的辅助工具类
 * 
 * 提供统一的方法来加载技能的多语言句子数据
 */
object MultiLanguageHelper {
    private const val TAG = "MultiLanguageHelper"
    
    /**
     * 支持的语言列表 (中文、英语、韩语)
     */
    val SUPPORTED_LANGUAGES = listOf("cn", "en", "ko")
    
    /**
     * 加载指定技能的所有支持语言数据
     * 
     * @param skillId 技能ID,用于日志记录
     * @param languageDataGetter 根据语言代码获取句子数据的函数,例如 Sentences.Weather::get
     * @return 所有成功加载的语言数据列表
     */
    fun <T> loadAllLanguageData(
        skillId: String,
        languageDataGetter: (String) -> StandardRecognizerData<T>?
    ): List<StandardRecognizerData<T>> {
        val allData = mutableListOf<StandardRecognizerData<T>>()
        
        for (lang in SUPPORTED_LANGUAGES) {
            val data = languageDataGetter(lang)
            if (data != null) {
                allData.add(data)
                Log.d(TAG, "✅ [$skillId] 加载语言: $lang")
            } else {
                Log.w(TAG, "⚠️ [$skillId] 未找到语言: $lang")
            }
        }
        
        if (allData.isEmpty()) {
            Log.e(TAG, "❌ [$skillId] 未加载任何语言数据")
        } else {
            Log.d(TAG, "🌐 [$skillId] 技能支持 ${allData.size} 种语言: $SUPPORTED_LANGUAGES")
        }
        
        return allData
    }
}

