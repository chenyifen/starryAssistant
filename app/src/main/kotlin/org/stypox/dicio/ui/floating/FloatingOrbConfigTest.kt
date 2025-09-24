package org.stypox.dicio.ui.floating

import android.content.Context
import org.stypox.dicio.util.DebugLogger

/**
 * 悬浮球配置测试工具
 * 
 * 用于测试和验证FloatingOrbConfig的各种功能
 */
object FloatingOrbConfigTest {
    
    private val TAG = "FloatingOrbConfigTest"
    
    /**
     * 运行所有测试
     */
    fun runAllTests(context: Context) {
        DebugLogger.logUI(TAG, "🧪 Starting FloatingOrbConfig tests...")
        
        testInitialization(context)
        testScaleFactorAdjustment()
        testScreenBounds()
        testEdgeCalculations()
        testConfigValues()
        
        DebugLogger.logUI(TAG, "✅ All tests completed!")
    }
    
    /**
     * 测试初始化
     */
    private fun testInitialization(context: Context) {
        DebugLogger.logUI(TAG, "🔧 Testing initialization...")
        
        FloatingOrbConfig.initialize(context)
        
        val debugInfo = FloatingOrbConfig.getDebugInfo()
        DebugLogger.logUI(TAG, "📊 Config info:\n$debugInfo")
        
        // 验证基本值
        val orbSize = FloatingOrbConfig.orbSizeDp
        val animationSize = FloatingOrbConfig.animationSizeDp
        
        DebugLogger.logUI(TAG, "📏 Orb size: $orbSize, Animation size: $animationSize")
    }
    
    /**
     * 测试缩放因子调整
     */
    private fun testScaleFactorAdjustment() {
        DebugLogger.logUI(TAG, "🔍 Testing scale factor adjustment...")
        
        val originalFactor = FloatingOrbConfig.getScaleFactor()
        DebugLogger.logUI(TAG, "📐 Original scale factor: $originalFactor")
        
        // 测试设置不同的缩放因子
        val testFactors = listOf(1.5f, 2.0f, 3.5f, 5.0f, 6.0f) // 6.0f应该被限制为5.0f
        
        testFactors.forEach { factor ->
            FloatingOrbConfig.setScaleFactor(factor)
            val actualFactor = FloatingOrbConfig.getScaleFactor()
            val orbSize = FloatingOrbConfig.orbSizeDp
            
            DebugLogger.logUI(TAG, "🎯 Set: $factor, Actual: $actualFactor, Orb size: $orbSize")
        }
        
        // 恢复原始缩放因子
        FloatingOrbConfig.setScaleFactor(originalFactor)
    }
    
    /**
     * 测试屏幕边界
     */
    private fun testScreenBounds() {
        DebugLogger.logUI(TAG, "📱 Testing screen bounds...")
        
        val bounds = FloatingOrbConfig.getScreenBounds()
        
        DebugLogger.logUI(TAG, "📐 Screen: ${bounds.width}x${bounds.height}, Orb: ${bounds.orbSize}")
        DebugLogger.logUI(TAG, "📍 Max position: (${bounds.maxX}, ${bounds.maxY})")
        
        // 测试位置限制
        val testPositions = listOf(
            Pair(-100, -100), // 负值
            Pair(bounds.width + 100, bounds.height + 100), // 超出范围
            Pair(bounds.width / 2, bounds.height / 2) // 中心位置
        )
        
        testPositions.forEach { (x, y) ->
            val clampedX = bounds.clampX(x)
            val clampedY = bounds.clampY(y)
            DebugLogger.logUI(TAG, "🔒 ($x, $y) → ($clampedX, $clampedY)")
        }
    }
    
    /**
     * 测试边缘计算
     */
    private fun testEdgeCalculations() {
        DebugLogger.logUI(TAG, "🧭 Testing edge calculations...")
        
        val bounds = FloatingOrbConfig.getScreenBounds()
        
        val testPositions = listOf(
            Pair(0, bounds.height / 2), // 左边缘
            Pair(bounds.maxX, bounds.height / 2), // 右边缘
            Pair(bounds.width / 2, 0), // 上边缘
            Pair(bounds.width / 2, bounds.maxY), // 下边缘
            Pair(bounds.width / 2, bounds.height / 2) // 中心
        )
        
        testPositions.forEach { (x, y) ->
            val distances = bounds.getEdgeDistances(x, y)
            DebugLogger.logUI(TAG, "📍 ($x, $y): L=${distances.left}, R=${distances.right}, T=${distances.top}, B=${distances.bottom}, Min=${distances.min}, Nearest=${distances.nearestEdge}")
        }
    }
    
    /**
     * 测试配置值
     */
    private fun testConfigValues() {
        DebugLogger.logUI(TAG, "⚙️ Testing config values...")
        
        // 测试拖拽配置
        DebugLogger.logUI(TAG, "🤏 Drag config:")
        DebugLogger.logUI(TAG, "  - Long press timeout: ${FloatingOrbConfig.Drag.LONG_PRESS_TIMEOUT}ms")
        DebugLogger.logUI(TAG, "  - Click threshold: ${FloatingOrbConfig.Drag.CLICK_THRESHOLD}px")
        DebugLogger.logUI(TAG, "  - Edge snap threshold: ${FloatingOrbConfig.Drag.EDGE_SNAP_THRESHOLD}px")
        DebugLogger.logUI(TAG, "  - Default position: (${FloatingOrbConfig.Drag.DEFAULT_X}, ${FloatingOrbConfig.Drag.DEFAULT_Y})")
        
        // 测试动画配置
        DebugLogger.logUI(TAG, "🎬 Animation config:")
        DebugLogger.logUI(TAG, "  - Expand duration: ${FloatingOrbConfig.Animation.EXPAND_DURATION}ms")
        DebugLogger.logUI(TAG, "  - Contract duration: ${FloatingOrbConfig.Animation.CONTRACT_DURATION}ms")
        DebugLogger.logUI(TAG, "  - Wake word duration: ${FloatingOrbConfig.Animation.WAKE_WORD_DURATION}ms")
        DebugLogger.logUI(TAG, "  - Auto dismiss delay: ${FloatingOrbConfig.Animation.AUTO_DISMISS_DELAY}ms")
        
        // 测试尺寸值
        DebugLogger.logUI(TAG, "📏 Size values:")
        DebugLogger.logUI(TAG, "  - Orb size: ${FloatingOrbConfig.orbSizeDp} (${FloatingOrbConfig.orbSizePx}px)")
        DebugLogger.logUI(TAG, "  - Animation size: ${FloatingOrbConfig.animationSizeDp} (${FloatingOrbConfig.animationSizeInt})")
    }
}
