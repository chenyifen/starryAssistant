package com.ai.voice.skills.device_control

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.ifpdos.sdklib.hyundaiit.api.audio.AudioHelper
import com.ifpdos.sdklib.hyundaiit.api.screen.ScreenHelper
import com.ifpdos.sdklib.hyundaiit.api.source.SourceHelper
import com.ifpdos.sdklib.hyundaiit.api.system.SystemHelper
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.io.graphical.StringOutput

/**
 * 设备控制技能基类
 * 
 * 包含所有设备控制命令的执行方法，被各个子技能类共享
 */
abstract class BaseDeviceControlSkill {

    companion object {
        private const val TAG = "BaseDeviceControlSkill"
        
        // 广播Action常量（与服务端保持一致）
        private const val ACTION_DEVICE_CONTROL = "com.xiaozhi.DEVICE_CONTROL"
        private const val EXTRA_COMMAND = "command"
    }

    // ==================== 电源和音量控制 ====================
    
    fun executePowerOff(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行关机命令")
            ScreenHelper.getInstance().turnOffPower()
            return StringOutput("正在关闭电源")
        } catch (e: Exception) {
            Log.e(TAG, "关机失败", e)
            return StringOutput("关机失败")
        }
    }

    fun executePowerOn(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行开机命令")
            ScreenHelper.getInstance().turnOnPower()
            return StringOutput("正在开启电源")
        } catch (e: Exception) {
            Log.e(TAG, "开机失败", e)
            return StringOutput("开机失败")
        }
    }

    fun executeVolumeUp(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行音量增加命令")
            AudioHelper.getInstance().volumeUp()
            return StringOutput("音量已增加")
        } catch (e: Exception) {
            Log.e(TAG, "音量增加失败", e)
            return StringOutput("音量增加失败")
        }
    }

    fun executeVolumeDown(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行音量减小命令")
            AudioHelper.getInstance().volumeDown()
            return StringOutput("音量已减小")
        } catch (e: Exception) {
            Log.e(TAG, "音量减小失败", e)
            return StringOutput("音量减小失败")
        }
    }

    fun executeMute(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行静音命令")
            AudioHelper.getInstance().changeMuteStatus()
            return StringOutput("已静音")
        } catch (e: Exception) {
            Log.e(TAG, "静音失败", e)
            return StringOutput("静音失败")
        }
    }

    // ==================== 输入源切换 ====================
    
    fun executeInputSource(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "打开输入源选择")
            SystemHelper.getInstance().openInputSourceWindow(ctx.android)
            return StringOutput("正在打开输入源选择")
        } catch (e: Exception) {
            Log.e(TAG, "打开输入源选择失败", e)
            return StringOutput("打开输入源选择失败")
        }
    }

    fun executeHdmiOne(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 HDMI 1")
            SourceHelper.getInstance().switchToHDMI1()
            return StringOutput("正在切换到 HDMI 1")
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            return StringOutput("切换失败")
        }
    }

    fun executeHdmiTwo(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 HDMI 2")
            SourceHelper.getInstance().switchToHDMI2()
            return StringOutput("正在切换到 HDMI 2")
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            return StringOutput("切换失败")
        }
    }

    fun executeDpPort(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 DP 端口")
            SourceHelper.getInstance().switchToDP()
            return StringOutput("正在切换到 DP 端口")
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            return StringOutput("切换失败")
        }
    }

    fun executeFrontHdmi(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到前面板 HDMI")
            SourceHelper.getInstance().switchToFrontHDMI()
            return StringOutput("正在切换到前面板 HDMI")
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            return StringOutput("切换失败")
        }
    }

    fun executeFrontUsbC(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到前面板 USB-C")
            SourceHelper.getInstance().switchToFrontUSBC()
            return StringOutput("正在切换到前面板 USB-C")
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            return StringOutput("切换失败")
        }
    }

    fun executeOps(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 OPS")
            SourceHelper.getInstance().switchToOPS()
            return StringOutput("正在切换到 OPS")
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            return StringOutput("切换失败")
        }
    }

    // ==================== 应用启动 ====================
    
    fun executeGoogle(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().gotoGoogle(ctx.android)
            return StringOutput("正在打开Google")
        } catch (e: Exception) {
            Log.e(TAG, "打开Google失败", e)
            return StringOutput("打开Google失败")
        }
    }

    fun executeBrowser(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openBrowser(ctx.android)
            return StringOutput("正在打开浏览器")
        } catch (e: Exception) {
            Log.e(TAG, "打开浏览器失败", e)
            return StringOutput("打开浏览器失败")
        }
    }

    fun executePlayStore(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openPlayStore(ctx.android)
            return StringOutput("正在打开Play商店")
        } catch (e: Exception) {
            Log.e(TAG, "打开Play商店失败", e)
            return StringOutput("打开Play商店失败")
        }
    }

    fun executeYoutube(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openYoutube(ctx.android)
            return StringOutput("正在打开YouTube")
        } catch (e: Exception) {
            Log.e(TAG, "打开YouTube失败", e)
            return StringOutput("打开YouTube失败")
        }
    }

    fun executeSettings(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openSettings(ctx.android)
            return StringOutput("正在打开设置")
        } catch (e: Exception) {
            Log.e(TAG, "打开设置失败", e)
            return StringOutput("打开设置失败")
        }
    }

    fun executeCamera(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openCamera(ctx.android)
            return StringOutput("正在打开相机")
        } catch (e: Exception) {
            Log.e(TAG, "打开相机失败", e)
            return StringOutput("打开相机失败")
        }
    }

    fun executeRecorder(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openRecorder(ctx.android)
            return StringOutput("正在打开录音机")
        } catch (e: Exception) {
            Log.e(TAG, "打开录音机失败", e)
            return StringOutput("打开录音机失败")
        }
    }

    fun executeEshare(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openEShare(ctx.android)
            return StringOutput("正在打开E-Share")
        } catch (e: Exception) {
            Log.e(TAG, "打开E-Share失败", e)
            return StringOutput("打开E-Share失败")
        }
    }

    fun executeFinder(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openFinder(ctx.android)
            return StringOutput("正在打开文件管理器")
        } catch (e: Exception) {
            Log.e(TAG, "打开文件管理器失败", e)
            return StringOutput("打开文件管理器失败")
        }
    }

    // ==================== 白板工具 ====================
    
    fun executeWhiteboard(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "whiteboard")
    }

    fun executeSaveWhiteboard(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "save_whiteboard")
    }

    fun executeRedPen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "red_pen")
    }

    fun executeBluePen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "blue_pen")
    }

    fun executeWhitePen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "white_pen")
    }

    fun executeBlackPen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "black_pen")
    }

    fun executeEraser(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "eraser")
    }

    fun executeDeleteAll(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "delete_all")
    }

    fun executeHighlightPen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "highlight_pen")
    }

    fun executeFountainPen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "fountain_pen")
    }

    fun executeBrushPen(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "brush_pen")
    }

    // ==================== 系统导航和功能 ====================
    
    /**
     * 🆕 根据ASR语言获取多语言回复文本
     */
    private fun getLocalizedResponse(ctx: SkillContext, korean: String, english: String): String {
        val asrLocale = (ctx as? com.ai.voice.di.SkillContextInternal)?.asrLocale
        Log.d(TAG, "🔍 getLocalizedResponse - asrLocale: $asrLocale, language: ${asrLocale?.language}")
        return when {
            asrLocale?.language == "en" -> {
                Log.d(TAG, "✅ 使用英语回复: $english")
                english
            }
            asrLocale?.language == "ko" -> {
                Log.d(TAG, "✅ 使用韩语回复: $korean")
                korean
            }
            else -> {
                Log.d(TAG, "⚠️ 使用默认韩语回复: $korean")
                korean  // 默认韩语
            }
        }
    }
    
    fun executeHomeScreen(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().gotoHomeScreen(ctx.android)
            val response = getLocalizedResponse(ctx, "홈 화면으로 이동 중입니다", "Returning to home screen")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "返回主屏幕失败", e)
            val errorResponse = getLocalizedResponse(ctx, "홈 화면 이동 실패", "Failed to return to home screen")
            return StringOutput(errorResponse)
        }
    }

    fun executeGoBack(ctx: SkillContext): SkillOutput {
        try {
            sendKeyEvent(ctx, KeyEvent.KEYCODE_BACK)
            val response = getLocalizedResponse(ctx, "뒤로 가기", "Going back")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "返回失败", e)
            val errorResponse = getLocalizedResponse(ctx, "뒤로 가기 실패", "Failed to go back")
            return StringOutput(errorResponse)
        }
    }

    fun executeScreenshot(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行截图命令")
            SystemHelper.getInstance().takeScreenShot("")
            val response = getLocalizedResponse(ctx, "스크린샷 찍는 중", "Taking screenshot")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            val errorResponse = getLocalizedResponse(ctx, "스크린샷 실패", "Failed to take screenshot")
            return StringOutput(errorResponse)
        }
    }

    fun executeNoteMode(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "note_mode")
    }

    fun executeWindowMode(ctx: SkillContext): SkillOutput {
        return sendBroadcast(ctx, "window_mode")
    }

    fun executeWifiConnect(ctx: SkillContext): SkillOutput {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.android.startActivity(intent)
            val response = getLocalizedResponse(ctx, "WiFi 설정 열기", "Opening WiFi settings")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开WiFi设置失败", e)
            val errorResponse = getLocalizedResponse(ctx, "WiFi 설정 열기 실패", "Failed to open WiFi settings")
            return StringOutput(errorResponse)
        }
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 发送设备控制广播
     */
    fun sendBroadcast(ctx: SkillContext, command: String): SkillOutput {
        try {
            val intent = Intent(ACTION_DEVICE_CONTROL)
            intent.putExtra(EXTRA_COMMAND, command)
            intent.setPackage(ctx.android.packageName)
            ctx.android.sendBroadcast(intent)
            Log.d(TAG, "发送广播: $command")
            return StringOutput("正在执行: $command")
        } catch (e: Exception) {
            Log.e(TAG, "发送广播失败: $command", e)
            return StringOutput("执行失败")
        }
    }

    /**
     * 发送按键事件
     */
    private fun sendKeyEvent(ctx: SkillContext, keyCode: Int) {
        try {
            val audioManager = ctx.android.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val event = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            audioManager.dispatchMediaKeyEvent(event)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventUp)
        } catch (e: Exception) {
            Log.e(TAG, "发送按键事件失败: $keyCode", e)
        }
    }
}

