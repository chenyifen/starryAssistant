package org.stypox.dicio.io.wake

/**
 * 唤醒词检测回调接口
 * 
 * 用于在检测到唤醒词时通知其他组件
 */
interface WakeWordCallback {
    /**
     * 当检测到唤醒词时调用
     * 
     * @param confidence 置信度 (0.0 - 1.0)
     * @param wakeWord 检测到的唤醒词文本
     */
    fun onWakeWordDetected(confidence: Float = 1.0f, wakeWord: String = "")
    
    /**
     * 当唤醒词检测开始时调用
     */
    fun onWakeWordListeningStarted() {}
    
    /**
     * 当唤醒词检测停止时调用
     */
    fun onWakeWordListeningStopped() {}
    
    /**
     * 当唤醒词检测出现错误时调用
     */
    fun onWakeWordError(error: Throwable) {}
}

/**
 * 唤醒词回调管理器
 * 
 * 管理多个唤醒词回调的注册和通知
 */
object WakeWordCallbackManager {
    private val callbacks = mutableSetOf<WakeWordCallback>()
    
    /**
     * 注册唤醒词回调
     */
    fun registerCallback(callback: WakeWordCallback) {
        synchronized(callbacks) {
            callbacks.add(callback)
        }
    }
    
    /**
     * 取消注册唤醒词回调
     */
    fun unregisterCallback(callback: WakeWordCallback) {
        synchronized(callbacks) {
            callbacks.remove(callback)
        }
    }
    
    /**
     * 通知所有回调：检测到唤醒词
     */
    fun notifyWakeWordDetected(confidence: Float = 1.0f, wakeWord: String = "") {
        synchronized(callbacks) {
            callbacks.forEach { callback ->
                try {
                    callback.onWakeWordDetected(confidence, wakeWord)
                } catch (e: Exception) {
                    // 忽略回调中的异常，避免影响其他回调
                }
            }
        }
    }
    
    /**
     * 通知所有回调：开始监听
     */
    fun notifyListeningStarted() {
        synchronized(callbacks) {
            callbacks.forEach { callback ->
                try {
                    callback.onWakeWordListeningStarted()
                } catch (e: Exception) {
                    // 忽略回调中的异常
                }
            }
        }
    }
    
    /**
     * 通知所有回调：停止监听
     */
    fun notifyListeningStopped() {
        synchronized(callbacks) {
            callbacks.forEach { callback ->
                try {
                    callback.onWakeWordListeningStopped()
                } catch (e: Exception) {
                    // 忽略回调中的异常
                }
            }
        }
    }
    
    /**
     * 通知所有回调：检测错误
     */
    fun notifyError(error: Throwable) {
        synchronized(callbacks) {
            callbacks.forEach { callback ->
                try {
                    callback.onWakeWordError(error)
                } catch (e: Exception) {
                    // 忽略回调中的异常
                }
            }
        }
    }
    
    /**
     * 获取当前注册的回调数量
     */
    fun getCallbackCount(): Int {
        synchronized(callbacks) {
            return callbacks.size
        }
    }
}
