package com.ai.voice.skills.device_control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.ifpdos.sdklib.hyundaiit.api.audio.AudioHelper
import com.ifpdos.sdklib.hyundaiit.api.screen.ScreenHelper
import com.ifpdos.sdklib.hyundaiit.api.source.SourceHelper
import com.ifpdos.sdklib.hyundaiit.api.system.SystemHelper
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.MultiLanguageStandardRecognizerSkill
import com.ai.voice.sentences.Sentences.DeviceControl

class DeviceControlSkill(
    correspondingSkillInfo: SkillInfo,
    allLanguageData: List<StandardRecognizerData<DeviceControl>>,
) : MultiLanguageStandardRecognizerSkill<DeviceControl>(correspondingSkillInfo, allLanguageData) {

    companion object {
        private const val TAG = "DeviceControlSkill"
        
        // 广播Action常量（与服务端保持一致）
        private const val ACTION_DEVICE_CONTROL = "com.xiaozhi.DEVICE_CONTROL"
        private const val EXTRA_COMMAND = "command"
        
        // 用于存储score调用的时间戳（线程局部变量）
        private val scoreTimestamp = ThreadLocal<Long>()
    }
    
    /**
     * 覆盖score方法以记录开始时间
     */
    override fun score(ctx: SkillContext, input: String): Pair<org.dicio.skill.skill.Score, DeviceControl> {
        val startTime = System.currentTimeMillis()
        scoreTimestamp.set(startTime)
        Log.d(TAG, "⏱️ [性能] DeviceControl.score() 开始: input='$input'")
        
        val result = super.score(ctx, input)
        
        val scoreTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "⏱️ [性能] DeviceControl.score() 完成: ${scoreTime}ms, 分数=${result.first.scoreIn01Range()}")
        
        return result
    }

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: DeviceControl
    ): SkillOutput {
        val executeStartTime = System.currentTimeMillis()
        val scoreTime = scoreTimestamp.get()
        
        if (scoreTime != null) {
            val totalSinceScore = executeStartTime - scoreTime
            Log.d(TAG, "⏱️ [性能] 从score到generateOutput的间隔: ${totalSinceScore}ms")
        }
        
        Log.d(TAG, "⏱️ [性能] DeviceControl.generateOutput() 开始: command=${inputData.javaClass.simpleName}")
        
        return try {
            // 直接执行命令或发送广播
            val output = when (inputData) {
                is DeviceControl.VolumeUp -> executeVolumeUp(ctx)
                is DeviceControl.VolumeDown -> executeVolumeDown(ctx)
                is DeviceControl.MuteOn -> executeMute(ctx)
                is DeviceControl.HomeScreen -> executeHomeScreen(ctx)
                is DeviceControl.Google -> executeGoogle(ctx)
                is DeviceControl.Browser -> executeBrowser(ctx)
                is DeviceControl.PlayStore -> executePlayStore(ctx)
                is DeviceControl.Youtube -> executeYoutube(ctx)
                is DeviceControl.Settings -> executeSettings(ctx)
                is DeviceControl.Camera -> executeCamera(ctx)
                is DeviceControl.Screenshot -> executeScreenshot(ctx)
                is DeviceControl.GoBack -> executeGoBack(ctx)
                
                // 使用Hyundai IT API实现的命令
                is DeviceControl.PowerOff -> executePowerOff(ctx)
                is DeviceControl.PowerOn -> executePowerOn(ctx)
                is DeviceControl.InputSource -> executeInputSource(ctx)
                is DeviceControl.HdmiOne -> executeHdmiOne(ctx)
                is DeviceControl.HdmiTwo -> executeHdmiTwo(ctx)
                is DeviceControl.DpPort -> executeDpPort(ctx)
                is DeviceControl.FrontHdmi -> executeFrontHdmi(ctx)
                is DeviceControl.FrontUsbC -> executeFrontUsbC(ctx)
                is DeviceControl.Ops -> executeOps(ctx)
                is DeviceControl.Whiteboard -> sendBroadcast(ctx, "whiteboard")
                is DeviceControl.SaveWhiteboard -> sendBroadcast(ctx, "save_whiteboard")
                is DeviceControl.RedPen -> sendBroadcast(ctx, "red_pen")
                is DeviceControl.BluePen -> sendBroadcast(ctx, "blue_pen")
                is DeviceControl.WhitePen -> sendBroadcast(ctx, "white_pen")
                is DeviceControl.BlackPen -> sendBroadcast(ctx, "black_pen")
                is DeviceControl.Eraser -> sendBroadcast(ctx, "eraser")
                is DeviceControl.DeleteAll -> sendBroadcast(ctx, "delete_all")
                is DeviceControl.HighlightPen -> sendBroadcast(ctx, "highlight_pen")
                is DeviceControl.FountainPen -> sendBroadcast(ctx, "fountain_pen")
                is DeviceControl.BrushPen -> sendBroadcast(ctx, "brush_pen")
                is DeviceControl.Recorder -> executeRecorder(ctx)
                is DeviceControl.Eshare -> executeEshare(ctx)
                is DeviceControl.Finder -> executeFinder(ctx)
            }
            
            // 记录执行耗时
            val executeTime = System.currentTimeMillis() - executeStartTime
            val totalTime = if (scoreTime != null) {
                System.currentTimeMillis() - scoreTime
            } else {
                executeTime
            }
            
            Log.d(TAG, "⏱️ [性能] DeviceControl.generateOutput() 完成: ${executeTime}ms")
            Log.d(TAG, "⏱️ [性能] ========== DeviceControl 总耗时: ${totalTime}ms ==========")
            
            // 清理线程局部变量
            scoreTimestamp.remove()
            
            output
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute device control", e)
            
            // 即使出错也记录耗时
            val executeTime = System.currentTimeMillis() - executeStartTime
            val totalTime = if (scoreTime != null) {
                System.currentTimeMillis() - scoreTime
            } else {
                executeTime
            }
            
            Log.d(TAG, "⏱️ [性能] DeviceControl执行失败: ${executeTime}ms")
            Log.d(TAG, "⏱️ [性能] ========== DeviceControl 总耗时(失败): ${totalTime}ms ==========")
            
            // 清理线程局部变量
            scoreTimestamp.remove()
            
            DeviceControlOutput(
                command = "error",
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    // ========== 使用Hyundai IT API实现的命令 ==========
    
    /**
     * 电源控制 - 关机
     */
    private fun executePowerOff(ctx: SkillContext): SkillOutput {
        return try {
            ScreenHelper.getInstance().turnOffPower()
            Log.d(TAG, "✅ Power off")
            DeviceControlOutput("power_off", true, "Turning off the power")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to turn off power", e)
            DeviceControlOutput("power_off", false, "Failed to turn off power: ${e.message}")
        }
    }
    
    /**
     * 电源控制 - 开机
     */
    private fun executePowerOn(ctx: SkillContext): SkillOutput {
        return try {
            ScreenHelper.getInstance().turnOnPower()
            Log.d(TAG, "✅ Power on")
            DeviceControlOutput("power_on", true, "Turning on the power")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to turn on power", e)
            DeviceControlOutput("power_on", false, "Failed to turn on power: ${e.message}")
        }
    }
    
    /**
     * 音量增加
     */
    private fun executeVolumeUp(ctx: SkillContext): SkillOutput {
        return try {
            AudioHelper.getInstance().volumeUp()
            Log.d(TAG, "✅ Volume increased")
            DeviceControlOutput("volume_up", true, "Volume increased")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to increase volume", e)
            DeviceControlOutput("volume_up", false, "Failed to increase volume: ${e.message}")
        }
    }
    
    /**
     * 音量减少
     */
    private fun executeVolumeDown(ctx: SkillContext): SkillOutput {
        return try {
            AudioHelper.getInstance().volumeDown()
            Log.d(TAG, "✅ Volume decreased")
            DeviceControlOutput("volume_down", true, "Volume decreased")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to decrease volume", e)
            DeviceControlOutput("volume_down", false, "Failed to decrease volume: ${e.message}")
        }
    }
    
    /**
     * 静音/取消静音
     */
    private fun executeMute(ctx: SkillContext): SkillOutput {
        return try {
            AudioHelper.getInstance().changeMuteStatus()
            val isMuted = AudioHelper.getInstance().isMuteOn
            Log.d(TAG, "✅ Mute status changed: $isMuted")
            DeviceControlOutput("mute_on", true, if (isMuted) "Muted" else "Unmuted")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to change mute status", e)
            DeviceControlOutput("mute_on", false, "Failed to change mute status: ${e.message}")
        }
    }
    
    /**
     * 打开输入源窗口
     */
    private fun executeInputSource(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().openInputSourceWindow(ctx.android)
            Log.d(TAG, "✅ Input source window opened")
            DeviceControlOutput("input_source", true, "Opening input source window")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open input source window", e)
            DeviceControlOutput("input_source", false, "Failed to open input source window: ${e.message}")
        }
    }
    
    /**
     * 切换到HDMI 1
     */
    private fun executeHdmiOne(ctx: SkillContext): SkillOutput {
        return try {
            SourceHelper.getInstance().switchToHDMI1()
            Log.d(TAG, "✅ Switched to HDMI 1")
            DeviceControlOutput("hdmi_one", true, "Switching to HDMI 1")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to HDMI 1", e)
            DeviceControlOutput("hdmi_one", false, "Failed to switch to HDMI 1: ${e.message}")
        }
    }
    
    /**
     * 切换到HDMI 2
     */
    private fun executeHdmiTwo(ctx: SkillContext): SkillOutput {
        return try {
            SourceHelper.getInstance().switchToHDMI2()
            Log.d(TAG, "✅ Switched to HDMI 2")
            DeviceControlOutput("hdmi_two", true, "Switching to HDMI 2")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to HDMI 2", e)
            DeviceControlOutput("hdmi_two", false, "Failed to switch to HDMI 2: ${e.message}")
        }
    }
    
    /**
     * 切换到DP端口
     */
    private fun executeDpPort(ctx: SkillContext): SkillOutput {
        return try {
            SourceHelper.getInstance().switchToDP()
            Log.d(TAG, "✅ Switched to DP port")
            DeviceControlOutput("dp_port", true, "Switching to DP port")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to DP port", e)
            DeviceControlOutput("dp_port", false, "Failed to switch to DP port: ${e.message}")
        }
    }
    
    /**
     * 切换到前置HDMI
     */
    private fun executeFrontHdmi(ctx: SkillContext): SkillOutput {
        return try {
            SourceHelper.getInstance().switchToFrontHDMI()
            Log.d(TAG, "✅ Switched to front HDMI")
            DeviceControlOutput("front_hdmi", true, "Switching to front HDMI")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to front HDMI", e)
            DeviceControlOutput("front_hdmi", false, "Failed to switch to front HDMI: ${e.message}")
        }
    }
    
    /**
     * 切换到前置USB-C
     */
    private fun executeFrontUsbC(ctx: SkillContext): SkillOutput {
        return try {
            SourceHelper.getInstance().switchToFrontUSBC()
            Log.d(TAG, "✅ Switched to front USB-C")
            DeviceControlOutput("front_usb_c", true, "Switching to front USB-C")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to front USB-C", e)
            DeviceControlOutput("front_usb_c", false, "Failed to switch to front USB-C: ${e.message}")
        }
    }
    
    /**
     * 切换到OPS
     */
    private fun executeOps(ctx: SkillContext): SkillOutput {
        return try {
            SourceHelper.getInstance().switchToOPS()
            Log.d(TAG, "✅ Switched to OPS")
            DeviceControlOutput("ops", true, "Switching to OPS")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to OPS", e)
            DeviceControlOutput("ops", false, "Failed to switch to OPS: ${e.message}")
        }
    }
    
    /**
     * 回到主屏幕
     */
    private fun executeHomeScreen(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().gotoHomeScreen(ctx.android)
            Log.d(TAG, "✅ Going to home screen")
            DeviceControlOutput("home_screen", true, "Going to home screen")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to go to home screen", e)
            DeviceControlOutput("home_screen", false, "Failed to go to home screen: ${e.message}")
        }
    }
    
    /**
     * 打开Google
     */
    private fun executeGoogle(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().gotoGoogle(ctx.android)
            Log.d(TAG, "✅ Opening Google")
            DeviceControlOutput("google", true, "Opening Google")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open Google", e)
            DeviceControlOutput("google", false, "Failed to open Google: ${e.message}")
        }
    }
    
    /**
     * 打开浏览器
     */
    private fun executeBrowser(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().openBrowser(ctx.android)
            Log.d(TAG, "✅ Opening browser")
            DeviceControlOutput("browser", true, "Opening browser")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open browser", e)
            DeviceControlOutput("browser", false, "Failed to open browser: ${e.message}")
        }
    }
    
    /**
     * 打开Play Store
     */
    private fun executePlayStore(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().openPlayStore(ctx.android)
            Log.d(TAG, "✅ Opening Play Store")
            DeviceControlOutput("play_store", true, "Opening Play Store")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open Play Store", e)
            DeviceControlOutput("play_store", false, "Failed to open Play Store: ${e.message}")
        }
    }
    
    /**
     * 打开YouTube
     */
    private fun executeYoutube(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().openYoutube(ctx.android)
            Log.d(TAG, "✅ Opening YouTube")
            DeviceControlOutput("youtube", true, "Opening YouTube")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open YouTube", e)
            DeviceControlOutput("youtube", false, "Failed to open YouTube: ${e.message}")
        }
    }
    
    /**
     * 打开设置
     */
    private fun executeSettings(ctx: SkillContext): SkillOutput {
        return try {
            val success = SystemHelper.getInstance().openSettings(ctx.android)
            if (success) {
                Log.d(TAG, "✅ Opening settings")
                DeviceControlOutput("settings", true, "Opening settings")
            } else {
                Log.w(TAG, "⚠️ Failed to open settings")
                DeviceControlOutput("settings", false, "Failed to open settings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception when opening settings", e)
            DeviceControlOutput("settings", false, "Failed to open settings: ${e.message}")
        }
    }
    
    /**
     * 打开相机
     */
    private fun executeCamera(ctx: SkillContext): SkillOutput {
        return try {
            val success = SystemHelper.getInstance().openCamera(ctx.android)
            if (success) {
                Log.d(TAG, "✅ Opening camera")
                DeviceControlOutput("camera", true, "Opening camera")
            } else {
                Log.w(TAG, "⚠️ Failed to open camera")
                DeviceControlOutput("camera", false, "Camera app not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception when opening camera", e)
            DeviceControlOutput("camera", false, "Failed to open camera: ${e.message}")
        }
    }
    
    /**
     * 截图
     */
    private fun executeScreenshot(ctx: SkillContext): SkillOutput {
        return try {
            // 使用空字符串或临时路径
            SystemHelper.getInstance().takeScreenShot("")
            Log.d(TAG, "✅ Taking screenshot")
            DeviceControlOutput("screenshot", true, "Taking screenshot")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to take screenshot", e)
            DeviceControlOutput("screenshot", false, "Failed to take screenshot: ${e.message}")
        }
    }
    
    /**
     * 打开录音机
     */
    private fun executeRecorder(ctx: SkillContext): SkillOutput {
        return try {
            SystemHelper.getInstance().openRecorder(ctx.android)
            Log.d(TAG, "✅ Opening recorder")
            DeviceControlOutput("recorder", true, "Opening recorder")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open recorder", e)
            DeviceControlOutput("recorder", false, "Failed to open recorder: ${e.message}")
        }
    }
    
    /**
     * 打开E-Share
     */
    private fun executeEshare(ctx: SkillContext): SkillOutput {
        return try {
            val success = SystemHelper.getInstance().openEShare(ctx.android)
            if (success) {
                Log.d(TAG, "✅ Opening E-Share")
                DeviceControlOutput("eshare", true, "Opening E-Share")
            } else {
                Log.w(TAG, "⚠️ Failed to open E-Share")
                DeviceControlOutput("eshare", false, "E-Share app not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception when opening E-Share", e)
            DeviceControlOutput("eshare", false, "Failed to open E-Share: ${e.message}")
        }
    }
    
    /**
     * 打开Finder
     */
    private fun executeFinder(ctx: SkillContext): SkillOutput {
        return try {
            val success = SystemHelper.getInstance().openFinder(ctx.android)
            if (success) {
                Log.d(TAG, "✅ Opening Finder")
                DeviceControlOutput("finder", true, "Opening Finder")
            } else {
                Log.w(TAG, "⚠️ Failed to open Finder")
                DeviceControlOutput("finder", false, "Finder app not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception when opening Finder", e)
            DeviceControlOutput("finder", false, "Failed to open Finder: ${e.message}")
        }
    }
    
    /**
     * 返回（模拟返回键）
     */
    private fun executeGoBack(ctx: SkillContext): SkillOutput {
        // 方法1: 发送BACK键事件（需要INJECT_EVENTS权限）
        // 方法2: 使用无障碍服务（需要用户授权）
        // 方法3: 发送广播让外部服务处理
        
        // 这里使用发送广播的方式，让有权限的服务处理
        sendBroadcastOnly(ctx, "go_back")
        Log.d(TAG, "✅ Go back command sent")
        return DeviceControlOutput("go_back", true, "Going back")
    }
    
    // ========== 发送广播的命令 ==========
    
    /**
     * 发送广播给外部处理，并返回Output
     */
    private fun sendBroadcast(ctx: SkillContext, commandName: String): SkillOutput {
        sendBroadcastOnly(ctx, commandName)
        Log.d(TAG, "📡 Broadcast sent: $commandName")
        return DeviceControlOutput(
            command = commandName,
            success = true,
            message = getSuccessMessage(ctx, commandName)
        )
    }
    
    /**
     * 仅发送广播，不返回Output
     */
    private fun sendBroadcastOnly(ctx: SkillContext, commandName: String) {
        val intent = Intent(ACTION_DEVICE_CONTROL).apply {
            putExtra(EXTRA_COMMAND, commandName)
            // 使用显式广播以提高兼容性
            setPackage(ctx.android.packageName)
        }
        ctx.android.sendBroadcast(intent)
    }

    /**
     * 根据当前语言返回成功消息
     */
    private fun getSuccessMessage(ctx: SkillContext, command: String): String {
        val lang = ctx.locale.language
        
        return when (command) {
            "power_off" -> when (lang) {
                "ko" -> "전원을 끕니다"
                "zh" -> "正在关闭电源"
                else -> "Turning off the power"
            }
            "power_on" -> when (lang) {
                "ko" -> "전원을 켭니다"
                "zh" -> "正在打开电源"
                else -> "Turning on the power"
            }
            "volume_up" -> when (lang) {
                "ko" -> "볼륨을 높입니다"
                "zh" -> "音量已增加"
                else -> "Volume increased"
            }
            "volume_down" -> when (lang) {
                "ko" -> "볼륨을 낮춥니다"
                "zh" -> "音量已降低"
                else -> "Volume decreased"
            }
            "mute_on" -> when (lang) {
                "ko" -> "음소거됨"
                "zh" -> "已静音"
                else -> "Muted"
            }
            "input_source" -> when (lang) {
                "ko" -> "입력 소스 창을 엽니다"
                "zh" -> "正在打开输入源窗口"
                else -> "Opening input source"
            }
            "hdmi_one" -> when (lang) {
                "ko" -> "HDMI 1로 전환합니다"
                "zh" -> "正在切换到 HDMI 1"
                else -> "Switching to HDMI 1"
            }
            "hdmi_two" -> when (lang) {
                "ko" -> "HDMI 2로 전환합니다"
                "zh" -> "正在切换到 HDMI 2"
                else -> "Switching to HDMI 2"
            }
            "dp_port" -> when (lang) {
                "ko" -> "DP 포트로 전환합니다"
                "zh" -> "正在切换到 DP 端口"
                else -> "Switching to DP port"
            }
            "front_hdmi" -> when (lang) {
                "ko" -> "전면 HDMI로 전환합니다"
                "zh" -> "正在切换到前置 HDMI"
                else -> "Switching to front HDMI"
            }
            "front_usb_c" -> when (lang) {
                "ko" -> "전면 USB-C로 전환합니다"
                "zh" -> "正在切换到前置 USB-C"
                else -> "Switching to front USB-C"
            }
            "ops" -> when (lang) {
                "ko" -> "OPS로 전환합니다"
                "zh" -> "正在切换到 OPS"
                else -> "Switching to OPS"
            }
            "home_screen" -> when (lang) {
                "ko" -> "홈 화면으로 이동합니다"
                "zh" -> "正在前往主屏幕"
                else -> "Going to home screen"
            }
            "google" -> when (lang) {
                "ko" -> "구글을 엽니다"
                "zh" -> "正在打开谷歌"
                else -> "Opening Google"
            }
            "browser" -> when (lang) {
                "ko" -> "브라우저를 엽니다"
                "zh" -> "正在打开浏览器"
                else -> "Opening browser"
            }
            "play_store" -> when (lang) {
                "ko" -> "플레이 스토어를 엽니다"
                "zh" -> "正在打开应用商店"
                else -> "Opening Play Store"
            }
            "youtube" -> when (lang) {
                "ko" -> "유튜브를 엽니다"
                "zh" -> "正在打开YouTube"
                else -> "Opening YouTube"
            }
            "whiteboard" -> when (lang) {
                "ko" -> "화이트보드를 엽니다"
                "zh" -> "正在打开白板"
                else -> "Opening whiteboard"
            }
            "save_whiteboard" -> when (lang) {
                "ko" -> "화이트보드를 저장합니다"
                "zh" -> "正在保存白板"
                else -> "Saving whiteboard"
            }
            "red_pen" -> when (lang) {
                "ko" -> "빨간 펜을 선택했습니다"
                "zh" -> "已选择红笔"
                else -> "Red pen selected"
            }
            "blue_pen" -> when (lang) {
                "ko" -> "파란 펜을 선택했습니다"
                "zh" -> "已选择蓝笔"
                else -> "Blue pen selected"
            }
            "white_pen" -> when (lang) {
                "ko" -> "흰 펜을 선택했습니다"
                "zh" -> "已选择白笔"
                else -> "White pen selected"
            }
            "black_pen" -> when (lang) {
                "ko" -> "검은 펜을 선택했습니다"
                "zh" -> "已选择黑笔"
                else -> "Black pen selected"
            }
            "eraser" -> when (lang) {
                "ko" -> "지우개를 선택했습니다"
                "zh" -> "已选择橡皮擦"
                else -> "Eraser selected"
            }
            "delete_all" -> when (lang) {
                "ko" -> "모든 내용을 지웁니다"
                "zh" -> "正在清除所有内容"
                else -> "Clearing all content"
            }
            "highlight_pen" -> when (lang) {
                "ko" -> "형광펜을 선택했습니다"
                "zh" -> "已选择荧光笔"
                else -> "Highlight pen selected"
            }
            "fountain_pen" -> when (lang) {
                "ko" -> "만년필을 선택했습니다"
                "zh" -> "已选择钢笔"
                else -> "Fountain pen selected"
            }
            "brush_pen" -> when (lang) {
                "ko" -> "붓펜을 선택했습니다"
                "zh" -> "已选择毛笔"
                else -> "Brush pen selected"
            }
            "settings" -> when (lang) {
                "ko" -> "설정을 엽니다"
                "zh" -> "正在打开设置"
                else -> "Opening settings"
            }
            "recorder" -> when (lang) {
                "ko" -> "녹음기를 엽니다"
                "zh" -> "正在打开录音机"
                else -> "Opening recorder"
            }
            "eshare" -> when (lang) {
                "ko" -> "이셰어를 엽니다"
                "zh" -> "正在打开屏幕共享"
                else -> "Opening E-share"
            }
            "camera" -> when (lang) {
                "ko" -> "카메라를 엽니다"
                "zh" -> "正在打开相机"
                else -> "Opening camera"
            }
            "screenshot" -> when (lang) {
                "ko" -> "스크린샷을 찍습니다"
                "zh" -> "正在截屏"
                else -> "Taking screenshot"
            }
            "finder" -> when (lang) {
                "ko" -> "파인더를 엽니다"
                "zh" -> "正在打开文件管理器"
                else -> "Opening finder"
            }
            "go_back" -> when (lang) {
                "ko" -> "뒤로 갑니다"
                "zh" -> "正在返回"
                else -> "Going back"
            }
            else -> when (lang) {
                "ko" -> "명령이 실행되었습니다"
                "zh" -> "命令已执行"
                else -> "Command executed"
            }
        }
    }
}

