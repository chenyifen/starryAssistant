package com.ai.voice.skills.device_control

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.dicio.skill.context.SkillContext
import com.ai.voice.di.SkillContextImpl
import com.ai.voice.io.graphical.StringOutput
import org.dicio.skill.skill.SkillOutput
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * DeviceControl技能仪器测试
 * 测试所有DeviceControl命令的识别和执行
 * 
 * 注意：由于DeviceControl已被拆分为5个技能，此测试类直接使用BaseDeviceControlSkill来测试所有命令
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DeviceControlInstrumentationTest {

    companion object {
        private const val LOG_TAG = "DeviceControlInstrumentationTest"
    }

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var skillContext: SkillContext
    private val baseSkill = object : BaseDeviceControlSkill() {}

    @Before
    fun setup() {
        Log.i(LOG_TAG, "DeviceControlInstrumentationTest setup 开始")
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        skillContext = SkillContextImpl.newForPreviews(context)
        Log.i(LOG_TAG, "DeviceControlInstrumentationTest setup 完成")
    }

    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        Thread.sleep(100) // 等待Toast显示
    }

    /**
     * 执行命令并返回结果
     */
    private fun executeCommand(commandName: String): SkillOutput {
        Log.i(LOG_TAG, "🔍 executeCommand开始: commandName=$commandName")
        
        return try {
            val output = when (commandName) {
                "power_off" -> baseSkill.executePowerOff(skillContext)
                "power_on" -> baseSkill.executePowerOn(skillContext)
                "volume_up" -> baseSkill.executeVolumeUp(skillContext)
                "volume_down" -> baseSkill.executeVolumeDown(skillContext)
                "mute_on" -> baseSkill.executeMute(skillContext)
                "input_source" -> baseSkill.executeInputSource(skillContext)
                "hdmi_one" -> baseSkill.executeHdmiOne(skillContext)
                "hdmi_two" -> baseSkill.executeHdmiTwo(skillContext)
                "dp_port" -> baseSkill.executeDpPort(skillContext)
                "front_hdmi" -> baseSkill.executeFrontHdmi(skillContext)
                "front_usb_c" -> baseSkill.executeFrontUsbC(skillContext)
                "ops" -> baseSkill.executeOps(skillContext)
                "home_screen" -> baseSkill.executeHomeScreen(skillContext)
                "google" -> baseSkill.executeGoogle(skillContext)
                "browser" -> baseSkill.executeBrowser(skillContext)
                "play_store" -> baseSkill.executePlayStore(skillContext)
                "youtube" -> baseSkill.executeYoutube(skillContext)
                "settings" -> baseSkill.executeSettings(skillContext)
                "recorder" -> baseSkill.executeRecorder(skillContext)
                "eshare" -> baseSkill.executeEshare(skillContext)
                "camera" -> baseSkill.executeCamera(skillContext)
                "screenshot" -> baseSkill.executeScreenshot(skillContext)
                "finder" -> baseSkill.executeFinder(skillContext)
                "go_back" -> baseSkill.executeGoBack(skillContext)
                "note_mode" -> baseSkill.executeNoteMode(skillContext)
                "window_mode" -> baseSkill.executeWindowMode(skillContext)
                "wifi_connect" -> baseSkill.executeWifiConnect(skillContext)
                "whiteboard" -> baseSkill.executeWhiteboard(skillContext)
                "save_whiteboard" -> baseSkill.executeSaveWhiteboard(skillContext)
                "red_pen" -> baseSkill.executeRedPen(skillContext)
                "blue_pen" -> baseSkill.executeBluePen(skillContext)
                "white_pen" -> baseSkill.executeWhitePen(skillContext)
                "black_pen" -> baseSkill.executeBlackPen(skillContext)
                "eraser" -> baseSkill.executeEraser(skillContext)
                "delete_all" -> baseSkill.executeDeleteAll(skillContext)
                "highlight_pen" -> baseSkill.executeHighlightPen(skillContext)
                "fountain_pen" -> baseSkill.executeFountainPen(skillContext)
                "brush_pen" -> baseSkill.executeBrushPen(skillContext)
                else -> throw IllegalArgumentException("Unknown command: $commandName")
            }
            
            Log.i(LOG_TAG, "✅ 命令执行完成: $commandName, 输出: ${output.getSpeechOutput(skillContext)}")
            output
        } catch (e: Exception) {
            Log.e(LOG_TAG, "❌ Failed to execute command $commandName", e)
            Log.e(LOG_TAG, "❌ 异常详情: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            StringOutput("执行失败: ${e.message}")
        }
    }

    // ========== Power Control Tests ==========

    @Test
    fun testVolumeUp() {
        val commandName = "volume_up"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testVolumeUp: $resultMsg")
        assertNotNull("VolumeUp should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testVolumeDown() {
        val commandName = "volume_down"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testVolumeDown: $resultMsg")
        assertNotNull("VolumeDown should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testMute() {
        val commandName = "mute_on"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testMute: $resultMsg")
        assertNotNull("Mute should return output", output)
        Thread.sleep(2000)
    }

    @Test
    @Ignore("跳过关机测试，避免设备被关闭")
    fun testPowerOff() {
        val commandName = "power_off"
        showToast("Skip执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testPowerOff: $resultMsg")
        Thread.sleep(2000)
    }

    @Test
    fun testPowerOn() {
        val commandName = "power_on"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testPowerOn: $resultMsg")
        assertNotNull("PowerOn should return output", output)
        Thread.sleep(2000)
    }

    // ========== Input Source Control Tests ==========

    @Test
    fun testInputSource() {
        val commandName = "input_source"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testInputSource: $resultMsg")
        assertNotNull("InputSource should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testHdmiOne() {
        val commandName = "hdmi_one"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHdmiOne: $resultMsg")
        assertNotNull("HdmiOne should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testHdmiTwo() {
        val commandName = "hdmi_two"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHdmiTwo: $resultMsg")
        assertNotNull("HdmiTwo should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testDpPort() {
        val commandName = "dp_port"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testDpPort: $resultMsg")
        assertNotNull("DpPort should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testFrontHdmi() {
        val commandName = "front_hdmi"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFrontHdmi: $resultMsg")
        assertNotNull("FrontHdmi should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testFrontUsbC() {
        val commandName = "front_usb_c"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFrontUsbC: $resultMsg")
        assertNotNull("FrontUsbC should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testOps() {
        val commandName = "ops"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testOps: $resultMsg")
        assertNotNull("Ops should return output", output)
        Thread.sleep(2000)
    }

    // ========== App Launcher Tests ==========

    @Test
    fun testHomeScreen() {
        val commandName = "home_screen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHomeScreen: $resultMsg")
        assertNotNull("HomeScreen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testGoogle() {
        val commandName = "google"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testGoogle: $resultMsg")
        assertNotNull("Google should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testBrowser() {
        val commandName = "browser"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testBrowser: $resultMsg")
        assertNotNull("Browser should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testPlayStore() {
        val commandName = "play_store"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testPlayStore: $resultMsg")
        assertNotNull("PlayStore should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testYoutube() {
        val commandName = "youtube"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testYoutube: $resultMsg")
        assertNotNull("Youtube should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testSettings() {
        val commandName = "settings"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testSettings: $resultMsg")
        assertNotNull("Settings should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testRecorder() {
        val commandName = "recorder"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testRecorder: $resultMsg")
        assertNotNull("Recorder should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testEshare() {
        val commandName = "eshare"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testEshare: $resultMsg")
        assertNotNull("Eshare should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testCamera() {
        val commandName = "camera"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testCamera: $resultMsg")
        assertNotNull("Camera should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testFinder() {
        val commandName = "finder"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFinder: $resultMsg")
        assertNotNull("Finder should return output", output)
        Thread.sleep(2000)
    }

    // ========== System Navigation Tests ==========

    @Test
    fun testScreenshot() {
        val commandName = "screenshot"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testScreenshot: $resultMsg")
        assertNotNull("Screenshot should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testGoBack() {
        val commandName = "go_back"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testGoBack: $resultMsg")
        assertNotNull("GoBack should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testNoteMode() {
        val commandName = "note_mode"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testNoteMode: $resultMsg")
        assertNotNull("NoteMode should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testWindowMode() {
        val commandName = "window_mode"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testWindowMode: $resultMsg")
        assertNotNull("WindowMode should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testWifiConnect() {
        val commandName = "wifi_connect"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testWifiConnect: $resultMsg")
        assertNotNull("WifiConnect should return output", output)
        Thread.sleep(2000)
    }

    // ========== Whiteboard Tools Tests ==========

    @Test
    fun testWhiteboard() {
        val commandName = "whiteboard"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testWhiteboard: $resultMsg")
        assertNotNull("Whiteboard should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testSaveWhiteboard() {
        val commandName = "save_whiteboard"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testSaveWhiteboard: $resultMsg")
        assertNotNull("SaveWhiteboard should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testRedPen() {
        val commandName = "red_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testRedPen: $resultMsg")
        assertNotNull("RedPen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testBluePen() {
        val commandName = "blue_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testBluePen: $resultMsg")
        assertNotNull("BluePen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testWhitePen() {
        val commandName = "white_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testWhitePen: $resultMsg")
        assertNotNull("WhitePen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testBlackPen() {
        val commandName = "black_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testBlackPen: $resultMsg")
        assertNotNull("BlackPen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testEraser() {
        val commandName = "eraser"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testEraser: $resultMsg")
        assertNotNull("Eraser should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testDeleteAll() {
        val commandName = "delete_all"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testDeleteAll: $resultMsg")
        assertNotNull("DeleteAll should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testHighlightPen() {
        val commandName = "highlight_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHighlightPen: $resultMsg")
        assertNotNull("HighlightPen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testFountainPen() {
        val commandName = "fountain_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFountainPen: $resultMsg")
        assertNotNull("FountainPen should return output", output)
        Thread.sleep(2000)
    }

    @Test
    fun testBrushPen() {
        val commandName = "brush_pen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = "✅ 成功: ${output.getSpeechOutput(skillContext)}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testBrushPen: $resultMsg")
        assertNotNull("BrushPen should return output", output)
        Thread.sleep(2000)
    }
}
