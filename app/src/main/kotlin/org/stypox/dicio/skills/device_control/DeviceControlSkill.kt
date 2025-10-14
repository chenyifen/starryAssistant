package org.stypox.dicio.skills.device_control

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
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.DeviceControl

class DeviceControlSkill private constructor(
    correspondingSkillInfo: SkillInfo,
    data: StandardRecognizerData<DeviceControl>,
    private val isMultiLanguage: Boolean,
    private val allLanguageData: List<StandardRecognizerData<DeviceControl>>?
) : StandardRecognizerSkill<DeviceControl>(correspondingSkillInfo, data) {

    // 主构造函数 - 单语言模式
    constructor(
        correspondingSkillInfo: SkillInfo,
        data: StandardRecognizerData<DeviceControl>,
        isMultiLanguage: Boolean
    ) : this(correspondingSkillInfo, data, isMultiLanguage, null)
    
    // 次构造函数 - 多语言模式
    constructor(
        correspondingSkillInfo: SkillInfo,
        allData: List<StandardRecognizerData<DeviceControl>>,
        isMultiLanguage: Boolean
    ) : this(
        correspondingSkillInfo,
        allData.first(), // 使用第一个作为默认
        isMultiLanguage,
        allData
    )

    companion object {
        private const val TAG = "DeviceControlSkill"
        
        // 广播Action常量（与服务端保持一致）
        private const val ACTION_DEVICE_CONTROL = "com.xiaozhi.DEVICE_CONTROL"
        private const val EXTRA_COMMAND = "command"
    }
    
    /**
     * 覆盖父类的 score 方法以支持多语言匹配
     */
    override fun score(ctx: SkillContext, input: String): Pair<org.dicio.skill.skill.Score, DeviceControl> {
        // 如果不是多语言模式，直接调用父类方法
        if (!isMultiLanguage || allLanguageData == null) {
            return super.score(ctx, input)
        }
        
        // 多语言模式：尝试所有语言
        var bestResult: Pair<org.dicio.skill.skill.Score, DeviceControl>? = null
        var bestScore = 0.0
        
        Log.d(TAG, "🌐 多语言匹配开始: '$input'")
        
        for ((index, data) in allLanguageData.withIndex()) {
            try {
                val result = data.score(input)
                val score = result.first.scoreIn01Range().toDouble()
                
                Log.d(TAG, "  语言${index + 1} 匹配分数: $score")
                
                if (bestResult == null || score > bestScore) {
                    bestResult = result
                    bestScore = score
                }
            } catch (e: Exception) {
                Log.w(TAG, "  语言${index + 1} 匹配失败: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ 最佳匹配分数: $bestScore")
        
        return bestResult ?: throw IllegalStateException("No match found for input: $input")
    }

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: DeviceControl
    ): SkillOutput {
        return try {
            // 直接执行命令或发送广播
            when (inputData) {
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
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute device control", e)
            
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
            message = getSuccessMessage(commandName)
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

    private fun getSuccessMessage(command: String): String {
        return when (command) {
            "power_off" -> "Turning off the power"
            "power_on" -> "Turning on the power"
            "volume_up" -> "Volume increased"
            "volume_down" -> "Volume decreased"
            "mute_on" -> "Muted"
            "input_source" -> "Opening input source"
            "hdmi_one" -> "Switching to HDMI 1"
            "hdmi_two" -> "Switching to HDMI 2"
            "dp_port" -> "Switching to DP port"
            "front_hdmi" -> "Switching to front HDMI"
            "front_usb_c" -> "Switching to front USB-C"
            "ops" -> "Switching to OPS"
            "home_screen" -> "Going to home screen"
            "google" -> "Opening Google"
            "browser" -> "Opening browser"
            "play_store" -> "Opening Play Store"
            "youtube" -> "Opening YouTube"
            "whiteboard" -> "Opening whiteboard"
            "save_whiteboard" -> "Saving whiteboard"
            "red_pen" -> "Red pen selected"
            "blue_pen" -> "Blue pen selected"
            "white_pen" -> "White pen selected"
            "black_pen" -> "Black pen selected"
            "eraser" -> "Eraser selected"
            "delete_all" -> "Clearing all content"
            "highlight_pen" -> "Highlight pen selected"
            "fountain_pen" -> "Fountain pen selected"
            "brush_pen" -> "Brush pen selected"
            "settings" -> "Opening settings"
            "recorder" -> "Opening recorder"
            "eshare" -> "Opening E-share"
            "camera" -> "Opening camera"
            "screenshot" -> "Taking screenshot"
            "finder" -> "Opening finder"
            "go_back" -> "Going back"
            else -> "Command executed"
        }
    }
}

