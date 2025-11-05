package com.ai.voice.skills.device_control

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
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
            val response = getLocalizedResponse(ctx, "전원을 끄는 중입니다", "Turning off power")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "关机失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전원 끄기 실패", "Failed to turn off power")
            return StringOutput(errorResponse)
        }
    }

    fun executePowerOn(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行开机命令")
            ScreenHelper.getInstance().turnOnPower()
            val response = getLocalizedResponse(ctx, "전원을 켜는 중입니다", "Turning on power")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "开机失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전원 켜기 실패", "Failed to turn on power")
            return StringOutput(errorResponse)
        }
    }

    fun executeVolumeUp(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行音量增加命令")
            AudioHelper.getInstance().volumeUp()
            val response = getLocalizedResponse(ctx, "볼륨이 증가했습니다", "Volume increased")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "音量增加失败", e)
            val errorResponse = getLocalizedResponse(ctx, "볼륨 증가 실패", "Failed to increase volume")
            return StringOutput(errorResponse)
        }
    }

    fun executeVolumeDown(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行音量减小命令")
            AudioHelper.getInstance().volumeDown()
            val response = getLocalizedResponse(ctx, "볼륨이 감소했습니다", "Volume decreased")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "音量减小失败", e)
            val errorResponse = getLocalizedResponse(ctx, "볼륨 감소 실패", "Failed to decrease volume")
            return StringOutput(errorResponse)
        }
    }

    fun executeMute(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "执行静音命令")
            AudioHelper.getInstance().changeMuteStatus()
            val response = getLocalizedResponse(ctx, "음소거되었습니다", "Muted")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "静音失败", e)
            val errorResponse = getLocalizedResponse(ctx, "음소거 실패", "Failed to mute")
            return StringOutput(errorResponse)
        }
    }

    // ==================== 输入源切换 ====================
    
    fun executeInputSource(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "打开输入源选择")
            SystemHelper.getInstance().openInputSourceWindow(ctx.android)
            val response = getLocalizedResponse(ctx, "입력 소스 선택을 여는 중입니다", "Opening input source selection")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开输入源选择失败", e)
            val errorResponse = getLocalizedResponse(ctx, "입력 소스 선택 열기 실패", "Failed to open input source selection")
            return StringOutput(errorResponse)
        }
    }

    fun executeHdmiOne(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 HDMI 1")
            SourceHelper.getInstance().switchToHDMI1()
            val response = getLocalizedResponse(ctx, "HDMI 1로 전환 중입니다", "Switching to HDMI 1")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전환 실패", "Failed to switch")
            return StringOutput(errorResponse)
        }
    }

    fun executeHdmiTwo(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 HDMI 2")
            SourceHelper.getInstance().switchToHDMI2()
            val response = getLocalizedResponse(ctx, "HDMI 2로 전환 중입니다", "Switching to HDMI 2")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전환 실패", "Failed to switch")
            return StringOutput(errorResponse)
        }
    }

    fun executeDpPort(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 DP 端口")
            SourceHelper.getInstance().switchToDP()
            val response = getLocalizedResponse(ctx, "DP 포트로 전환 중입니다", "Switching to DP port")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전환 실패", "Failed to switch")
            return StringOutput(errorResponse)
        }
    }

    fun executeFrontHdmi(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到前面板 HDMI")
            SourceHelper.getInstance().switchToFrontHDMI()
            val response = getLocalizedResponse(ctx, "전면 패널 HDMI로 전환 중입니다", "Switching to front panel HDMI")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전환 실패", "Failed to switch")
            return StringOutput(errorResponse)
        }
    }

    fun executeFrontUsbC(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到前面板 USB-C")
            SourceHelper.getInstance().switchToFrontUSBC()
            val response = getLocalizedResponse(ctx, "전면 패널 USB-C로 전환 중입니다", "Switching to front panel USB-C")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전환 실패", "Failed to switch")
            return StringOutput(errorResponse)
        }
    }

    fun executeOps(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "切换到 OPS")
            SourceHelper.getInstance().switchToOPS()
            val response = getLocalizedResponse(ctx, "OPS로 전환 중입니다", "Switching to OPS")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "切换失败", e)
            val errorResponse = getLocalizedResponse(ctx, "전환 실패", "Failed to switch")
            return StringOutput(errorResponse)
        }
    }

    // ==================== 应用启动 ====================
    
    fun executeGoogle(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().gotoGoogle(ctx.android)
            val response = getLocalizedResponse(ctx, "Google을 여는 중입니다", "Opening Google")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开Google失败", e)
            val errorResponse = getLocalizedResponse(ctx, "Google 열기 실패", "Failed to open Google")
            return StringOutput(errorResponse)
        }
    }

    fun executeBrowser(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openBrowser(ctx.android)
            val response = getLocalizedResponse(ctx, "브라우저를 여는 중입니다", "Opening browser")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开浏览器失败", e)
            val errorResponse = getLocalizedResponse(ctx, "브라우저 열기 실패", "Failed to open browser")
            return StringOutput(errorResponse)
        }
    }

    fun executePlayStore(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openPlayStore(ctx.android)
            val response = getLocalizedResponse(ctx, "Play 스토어를 여는 중입니다", "Opening Play Store")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开Play商店失败", e)
            val errorResponse = getLocalizedResponse(ctx, "Play 스토어 열기 실패", "Failed to open Play Store")
            return StringOutput(errorResponse)
        }
    }

    fun executeYoutube(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openYoutube(ctx.android)
            val response = getLocalizedResponse(ctx, "YouTube를 여는 중입니다", "Opening YouTube")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开YouTube失败", e)
            val errorResponse = getLocalizedResponse(ctx, "YouTube 열기 실패", "Failed to open YouTube")
            return StringOutput(errorResponse)
        }
    }

    fun executeSettings(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openSettings(ctx.android)
            val response = getLocalizedResponse(ctx, "설정을 여는 중입니다", "Opening settings")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开设置失败", e)
            val errorResponse = getLocalizedResponse(ctx, "설정 열기 실패", "Failed to open settings")
            return StringOutput(errorResponse)
        }
    }

    fun executeCamera(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openCamera(ctx.android)
            val response = getLocalizedResponse(ctx, "카메라를 여는 중입니다", "Opening camera")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开相机失败", e)
            val errorResponse = getLocalizedResponse(ctx, "카메라 열기 실패", "Failed to open camera")
            return StringOutput(errorResponse)
        }
    }

    fun executeRecorder(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openRecorder(ctx.android)
            val response = getLocalizedResponse(ctx, "녹음기를 여는 중입니다", "Opening recorder")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开录音机失败", e)
            val errorResponse = getLocalizedResponse(ctx, "녹음기 열기 실패", "Failed to open recorder")
            return StringOutput(errorResponse)
        }
    }

    fun executeEshare(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openEShare(ctx.android)
            val response = getLocalizedResponse(ctx, "E-Share를 여는 중입니다", "Opening E-Share")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开E-Share失败", e)
            val errorResponse = getLocalizedResponse(ctx, "E-Share 열기 실패", "Failed to open E-Share")
            return StringOutput(errorResponse)
        }
    }

    fun executeFinder(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openFinder(ctx.android)
            val response = getLocalizedResponse(ctx, "파일 관리자를 여는 중입니다", "Opening file manager")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开文件管理器失败", e)
            val errorResponse = getLocalizedResponse(ctx, "파일 관리자 열기 실패", "Failed to open file manager")
            return StringOutput(errorResponse)
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
            
            val response = getLocalizedResponse(ctx, "명령 실행 중: $command", "Executing: $command")
            
            // 🆕 显示Toast提示
            Toast.makeText(ctx.android, response, Toast.LENGTH_SHORT).show()
            
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "发送广播失败: $command", e)
            val errorResponse = getLocalizedResponse(ctx, "실행 실패", "Execution failed")
            
            // 🆕 错误时也显示Toast
            Toast.makeText(ctx.android, errorResponse, Toast.LENGTH_SHORT).show()
            
            return StringOutput(errorResponse)
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

