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
                
                // 以下命令发送广播给外部处理
                is DeviceControl.PowerOff -> sendBroadcast(ctx, "power_off")
                is DeviceControl.PowerOn -> sendBroadcast(ctx, "power_on")
                is DeviceControl.InputSource -> sendBroadcast(ctx, "input_source")
                is DeviceControl.HdmiOne -> sendBroadcast(ctx, "hdmi_one")
                is DeviceControl.HdmiTwo -> sendBroadcast(ctx, "hdmi_two")
                is DeviceControl.DpPort -> sendBroadcast(ctx, "dp_port")
                is DeviceControl.FrontHdmi -> sendBroadcast(ctx, "front_hdmi")
                is DeviceControl.FrontUsbC -> sendBroadcast(ctx, "front_usb_c")
                is DeviceControl.Ops -> sendBroadcast(ctx, "ops")
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
                is DeviceControl.Recorder -> sendBroadcast(ctx, "recorder")
                is DeviceControl.Eshare -> sendBroadcast(ctx, "eshare")
                is DeviceControl.Finder -> sendBroadcast(ctx, "finder")
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
    
    // ========== 直接实现的命令 ==========
    
    /**
     * 音量增加
     */
    private fun executeVolumeUp(ctx: SkillContext): SkillOutput {
        val audioManager = ctx.android.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        Log.d(TAG, "✅ Volume increased")
        return DeviceControlOutput("volume_up", true, "Volume increased")
    }
    
    /**
     * 音量减少
     */
    private fun executeVolumeDown(ctx: SkillContext): SkillOutput {
        val audioManager = ctx.android.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        Log.d(TAG, "✅ Volume decreased")
        return DeviceControlOutput("volume_down", true, "Volume decreased")
    }
    
    /**
     * 静音
     */
    private fun executeMute(ctx: SkillContext): SkillOutput {
        val audioManager = ctx.android.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
        Log.d(TAG, "✅ Muted")
        return DeviceControlOutput("mute_on", true, "Muted")
    }
    
    /**
     * 回到主屏幕
     */
    private fun executeHomeScreen(ctx: SkillContext): SkillOutput {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.android.startActivity(intent)
        Log.d(TAG, "✅ Going to home screen")
        return DeviceControlOutput("home_screen", true, "Going to home screen")
    }
    
    /**
     * 打开Google
     */
    private fun executeGoogle(ctx: SkillContext): SkillOutput {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.android.startActivity(intent)
        Log.d(TAG, "✅ Opening Google")
        return DeviceControlOutput("google", true, "Opening Google")
    }
    
    /**
     * 打开浏览器
     */
    private fun executeBrowser(ctx: SkillContext): SkillOutput {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.android.startActivity(intent)
        Log.d(TAG, "✅ Opening browser")
        return DeviceControlOutput("browser", true, "Opening browser")
    }
    
    /**
     * 打开Play Store
     */
    private fun executePlayStore(ctx: SkillContext): SkillOutput {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            ctx.android.startActivity(intent)
            Log.d(TAG, "✅ Opening Play Store")
            return DeviceControlOutput("play_store", true, "Opening Play Store")
        } catch (e: Exception) {
            // 如果Play Store未安装，通过浏览器打开
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.android.startActivity(webIntent)
            return DeviceControlOutput("play_store", true, "Opening Play Store in browser")
        }
    }
    
    /**
     * 打开YouTube
     */
    private fun executeYoutube(ctx: SkillContext): SkillOutput {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.android.startActivity(intent)
        Log.d(TAG, "✅ Opening YouTube")
        return DeviceControlOutput("youtube", true, "Opening YouTube")
    }
    
    /**
     * 打开设置
     */
    private fun executeSettings(ctx: SkillContext): SkillOutput {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.android.startActivity(intent)
        Log.d(TAG, "✅ Opening settings")
        return DeviceControlOutput("settings", true, "Opening settings")
    }
    
    /**
     * 打开相机
     */
    private fun executeCamera(ctx: SkillContext): SkillOutput {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            ctx.android.startActivity(intent)
            Log.d(TAG, "✅ Opening camera")
            return DeviceControlOutput("camera", true, "Opening camera")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Camera app not found: ${e.message}")
            return DeviceControlOutput("camera", false, "Camera app not available")
        }
    }
    
    /**
     * 截图（发送按键事件）
     */
    private fun executeScreenshot(ctx: SkillContext): SkillOutput {
        // 截图需要系统权限，这里发送广播让系统服务处理
        // 也可以使用MediaProjection API，但需要用户授权
        sendBroadcastOnly(ctx, "screenshot")
        Log.d(TAG, "✅ Screenshot command sent")
        return DeviceControlOutput("screenshot", true, "Taking screenshot")
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

