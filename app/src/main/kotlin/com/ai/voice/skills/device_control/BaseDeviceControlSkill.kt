package com.ai.voice.skills.device_control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
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
        
        // EasiNote白板广播常量
        private const val ACTION_BOARD = "com.ifpdos.dasr.BOARD"
        private const val EXTRA_ID = "id"
        private const val BOARD_PACKAGE = "com.seewo.easinote"
        
        // EasiNote退出广播常量
        private const val ACTION_ASK_CLOSE_APP = "com.ifpdos.action.ASK_CLOSE_APP"
        private const val KEY_PACKAGE = "package"
        
        // EasiNote Activity
        private const val BOARD_ACTIVITY = "com.seewo.easinote.PlainWhiteboardActivity"
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
            val response = getLocalizedResponse(ctx, "볼륨을 높였습니다", "Volume increased")
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
            val response = getLocalizedResponse(ctx, "볼륨을 낮췄습니다", "Volume decreased")
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
            val response = getLocalizedResponse(ctx, "음소거 상태를 변경했습니다", "Mute status changed")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "静音失败", e)
            val errorResponse = getLocalizedResponse(ctx, "음소거 변경 실패", "Failed to change mute status")
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
            val response = getLocalizedResponse(ctx, "에이치디엠아이 일 연결해줘", "Switching to HDMI 1")
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
            val response = getLocalizedResponse(ctx, "에이치디엠아이 투 연결해줘", "Switching to HDMI 2")
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
            val response = getLocalizedResponse(ctx, "디피포트 연결해줘", "Switching to DP port")
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
            val response = getLocalizedResponse(ctx, "전면 에이치디엠아이 연결해줘", "Switching to front panel HDMI")
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
            val response = getLocalizedResponse(ctx, "유에스비 씨 연결해줘", "Switching to front panel USB-C")
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
            val response = getLocalizedResponse(ctx, "구글 연결해줘", "Opening Google")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开Google失败", e)
            val errorResponse = getLocalizedResponse(ctx, "구글 열기 실패", "Failed to open Google")
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
            val response = getLocalizedResponse(ctx, "플레이스토어 실행해줘", "Opening Play Store")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开Play商店失败", e)
            val errorResponse = getLocalizedResponse(ctx, "플레이스토어 열기 실패", "Failed to open Play Store")
            return StringOutput(errorResponse)
        }
    }

    fun executeYoutube(ctx: SkillContext): SkillOutput {
        try {
            SystemHelper.getInstance().openYoutube(ctx.android)
            val response = getLocalizedResponse(ctx, "유튜브 실행해줘", "Opening YouTube")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开YouTube失败", e)
            val errorResponse = getLocalizedResponse(ctx, "유튜브 열기 실패", "Failed to open YouTube")
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
            val response = getLocalizedResponse(ctx, "이쉐어 실행해줘", "Opening E-Share")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开E-Share失败", e)
            val errorResponse = getLocalizedResponse(ctx, "이쉐어 열기 실패", "Failed to open E-Share")
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
    
    /**
     * 打开白板（Note）
     */
    fun executeWhiteboard(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "打开白板")
            val intent = Intent().apply {
                component = ComponentName(BOARD_PACKAGE, BOARD_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.android.startActivity(intent)
            val response = getLocalizedResponse(ctx, "화이트보드를 여는 중입니다", "Opening whiteboard")
            showToastSafe(ctx.android, response)
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "打开白板失败", e)
            val errorResponse = getLocalizedResponse(ctx, "화이트보드 열기 실패", "Failed to open whiteboard")
            showToastSafe(ctx.android, errorResponse)
            return StringOutput(errorResponse)
        }
    }
    
    /**
     * 退出白板（Note）
     */
    fun executeExitWhiteboard(ctx: SkillContext): SkillOutput {
        try {
            Log.d(TAG, "退出白板")
            val intent = Intent(ACTION_ASK_CLOSE_APP).apply {
                putExtra(KEY_PACKAGE, BOARD_PACKAGE)
            }
            ctx.android.sendBroadcast(intent)
            val response = getLocalizedResponse(ctx, "화이트보드를 종료하는 중입니다", "Closing whiteboard")
            showToastSafe(ctx.android, response)
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "退出白板失败", e)
            val errorResponse = getLocalizedResponse(ctx, "화이트보드 종료 실패", "Failed to close whiteboard")
            showToastSafe(ctx.android, errorResponse)
            return StringOutput(errorResponse)
        }
    }

    /**
     * 发送白板控制广播
     */
    private fun sendBoardBroadcast(ctx: SkillContext, id: Int, koreanMsg: String, englishMsg: String, koreanError: String, englishError: String): SkillOutput {
        try {
            Log.d(TAG, "发送白板控制广播: id=$id")
            val intent = Intent(ACTION_BOARD).apply {
                putExtra(EXTRA_ID, id)
                setPackage(BOARD_PACKAGE)
            }
            ctx.android.sendBroadcast(intent)
            
            val response = getLocalizedResponse(ctx, koreanMsg, englishMsg)
            // 确保在主线程显示 Toast，避免 BinderProxy 错误
            showToastSafe(ctx.android, response)
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "发送白板控制广播失败: id=$id", e)
            val errorResponse = getLocalizedResponse(ctx, koreanError, englishError)
            // 确保在主线程显示 Toast，避免 BinderProxy 错误
            showToastSafe(ctx.android, errorResponse)
            return StringOutput(errorResponse)
        }
    }
    
    /**
     * 安全地在主线程显示 Toast（避免 BinderProxy 错误）
     */
    private fun showToastSafe(context: Context, message: String) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // 已经在主线程
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } else {
                // 在后台线程，切换到主线程
                Handler(Looper.getMainLooper()).post {
                    try {
                        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "显示 Toast 失败", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示 Toast 失败", e)
        }
    }

    fun executeSaveWhiteboard(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 32, "저장했습니다", "Saved", "저장 실패", "Failed to save")
    }

    fun executeRedPen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 33, "빨간 펜으로 변경했습니다", "Switched to red pen", "펜 변경 실패", "Failed to switch pen")
    }

    fun executeBluePen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 35, "파란 펜으로 변경했습니다", "Switched to blue pen", "펜 변경 실패", "Failed to switch pen")
    }

    fun executeWhitePen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 37, "흰 펜으로 변경했습니다", "Switched to white pen", "펜 변경 실패", "Failed to switch pen")
    }

    fun executeBlackPen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 38, "검은 펜으로 변경했습니다", "Switched to black pen", "펜 변경 실패", "Failed to switch pen")
    }

    fun executeEraser(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 39, "지우개 모드로 변경했습니다", "Switched to eraser mode", "모드 변경 실패", "Failed to switch mode")
    }

    fun executeDeleteAll(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 40, "화면을 지웠습니다", "Screen cleared", "지우기 실패", "Failed to clear")
    }

    fun executeHighlightPen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 41, "형광펜 모드로 변경했습니다", "Switched to highlighter mode", "모드 변경 실패", "Failed to switch mode")
    }

    fun executeFountainPen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 42, "만년필 모드로 변경했습니다", "Switched to fountain pen mode", "모드 변경 실패", "Failed to switch mode")
    }

    fun executeBrushPen(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 43, "붓펜 모드로 변경했습니다", "Switched to brush pen mode", "모드 변경 실패", "Failed to switch mode")
    }
    
    /**
     * 添加页面
     */
    fun executeAddPage(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 28, "페이지를 추가했습니다", "Page added", "페이지 추가 실패", "Failed to add page")
    }
    
    /**
     * 删除当前页
     */
    fun executeDeletePage(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 29, "현재 페이지를 삭제했습니다", "Current page deleted", "페이지 삭제 실패", "Failed to delete page")
    }
    
    /**
     * 下一页
     */
    fun executeNextPage(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 30, "다음 페이지로 이동했습니다", "Moved to next page", "페이지 이동 실패", "Failed to navigate")
    }
    
    /**
     * 上一页
     */
    fun executePreviousPage(ctx: SkillContext): SkillOutput {
        return sendBoardBroadcast(ctx, 31, "이전 페이지로 이동했습니다", "Moved to previous page", "페이지 이동 실패", "Failed to navigate")
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
            val response = getLocalizedResponse(ctx, "뒤로 갑니다", "Going back")
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
            val response = getLocalizedResponse(ctx, "스크린샷을 찍었습니다", "Screenshot taken")
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            val errorResponse = getLocalizedResponse(ctx, "스크린샷 실패", "Failed to take screenshot")
            return StringOutput(errorResponse)
        }
    }

    fun executeNoteMode(ctx: SkillContext): SkillOutput {
        return executeWhiteboard(ctx)
    }

    fun executeWindowMode(ctx: SkillContext): SkillOutput {
        return executeInputSource(ctx)
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
            
            // 🆕 显示Toast提示（安全地显示，避免 BinderProxy 错误）
            showToastSafe(ctx.android, response)
            
            return StringOutput(response)
        } catch (e: Exception) {
            Log.e(TAG, "发送广播失败: $command", e)
            val errorResponse = getLocalizedResponse(ctx, "실행 실패", "Execution failed")
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

