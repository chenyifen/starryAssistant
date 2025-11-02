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
import com.ai.voice.skills.device_control.DeviceControlSkill
import com.ai.voice.skills.device_control.DeviceControlInfo
import com.ai.voice.skills.device_control.DeviceControlOutput
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method
import javax.inject.Inject

/**
 * DeviceControl技能仪器测试
 * 测试所有DeviceControl命令的识别和执行
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
    private lateinit var deviceControlSkill: DeviceControlSkill

    @Before
    fun setup() {
        Log.i(LOG_TAG, "DeviceControlInstrumentationTest setup 开始")
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        skillContext = SkillContextImpl.newForPreviews(context)
        
        // 创建DeviceControlSkill实例
        deviceControlSkill = DeviceControlInfo.build(skillContext) as DeviceControlSkill
        Log.i(LOG_TAG, "DeviceControlSkill 实例创建成功")
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
     * 使用反射调用DeviceControlSkill的execute方法
     */
    private fun executeCommand(commandName: String): DeviceControlOutput {
        Log.i(LOG_TAG, "🔍 executeCommand开始: commandName=$commandName")
        
        val methodName = when (commandName) {
            "power_off" -> "executePowerOff"
            "power_on" -> "executePowerOn"
            "volume_up" -> "executeVolumeUp"
            "volume_down" -> "executeVolumeDown"
            "mute_on" -> "executeMute"
            "input_source" -> "executeInputSource"
            "hdmi_one" -> "executeHdmiOne"
            "hdmi_two" -> "executeHdmiTwo"
            "dp_port" -> "executeDpPort"
            "front_hdmi" -> "executeFrontHdmi"
            "front_usb_c" -> "executeFrontUsbC"
            "ops" -> "executeOps"
            "home_screen" -> "executeHomeScreen"
            "google" -> "executeGoogle"
            "browser" -> "executeBrowser"
            "play_store" -> "executePlayStore"
            "youtube" -> "executeYoutube"
            "settings" -> "executeSettings"
            "recorder" -> "executeRecorder"
            "eshare" -> "executeEshare"
            "camera" -> "executeCamera"
            "screenshot" -> "executeScreenshot"
            "finder" -> "executeFinder"
            "go_back" -> "executeGoBack"
            "note_mode" -> "executeNoteMode"
            "window_mode" -> "executeWindowMode"
            "wifi_connect" -> "executeWifiConnect"
            else -> throw IllegalArgumentException("Unknown command: $commandName")
        }

        Log.i(LOG_TAG, "🔍 映射方法名: $methodName for command: $commandName")

        try {
            Log.i(LOG_TAG, "🔍 开始获取方法: $methodName")
            val method: Method = DeviceControlSkill::class.java.getDeclaredMethod(methodName, SkillContext::class.java)
            method.isAccessible = true
            Log.i(LOG_TAG, "✅ 方法获取成功，开始调用: $methodName")
            
            val output = method.invoke(deviceControlSkill, skillContext) as DeviceControlOutput
            Log.i(LOG_TAG, "✅ 方法调用完成: $methodName, 返回结果: success=${output.success}, message=${output.message}")
            return output
        } catch (e: Exception) {
            Log.e(LOG_TAG, "❌ Failed to execute command $commandName", e)
            Log.e(LOG_TAG, "❌ 异常详情: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            return DeviceControlOutput(commandName, false, "执行失败: ${e.message}")
        }
    }

    // ========== 单个命令测试用例 ==========

    @Test
    fun testVolumeUp() {
        val commandName = "volume_up"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testVolumeUp: $resultMsg")
        assertTrue("VolumeUp should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testVolumeDown() {
        val commandName = "volume_down"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testVolumeDown: $resultMsg")
        assertTrue("VolumeDown should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testMute() {
        val commandName = "mute_on"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testMute: $resultMsg")
        assertTrue("Mute should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    @Ignore("跳过关机测试，避免设备被关闭")
    fun testPowerOff() {
        val commandName = "power_off"
        showToast("Skip执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testPowerOff: $resultMsg")
        // 注意：关机命令可能无法验证结果，所以不强制断言成功
        Thread.sleep(2000)
    }

    @Test
    fun testPowerOn() {
        val commandName = "power_on"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testPowerOn: $resultMsg")
        assertTrue("PowerOn should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testInputSource() {
        val commandName = "input_source"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testInputSource: $resultMsg")
        assertTrue("InputSource should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testHdmiOne() {
        val commandName = "hdmi_one"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHdmiOne: $resultMsg")
        assertTrue("HdmiOne should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testHdmiTwo() {
        val commandName = "hdmi_two"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHdmiTwo: $resultMsg")
        assertTrue("HdmiTwo should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testDpPort() {
        val commandName = "dp_port"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testDpPort: $resultMsg")
        assertTrue("DpPort should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testFrontHdmi() {
        val commandName = "front_hdmi"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFrontHdmi: $resultMsg")
        assertTrue("FrontHdmi should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testFrontUsbC() {
        val commandName = "front_usb_c"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFrontUsbC: $resultMsg")
        assertTrue("FrontUsbC should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testOps() {
        val commandName = "ops"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testOps: $resultMsg")
        assertTrue("Ops should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testHomeScreen() {
        val commandName = "home_screen"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testHomeScreen: $resultMsg")
        assertTrue("HomeScreen should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testGoogle() {
        val commandName = "google"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testGoogle: $resultMsg")
        assertTrue("Google should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testBrowser() {
        val commandName = "browser"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testBrowser: $resultMsg")
        assertTrue("Browser should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testPlayStore() {
        val commandName = "play_store"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testPlayStore: $resultMsg")
        assertTrue("PlayStore should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testYoutube() {
        val commandName = "youtube"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testYoutube: $resultMsg")
        assertTrue("Youtube should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testSettings() {
        val commandName = "settings"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testSettings: $resultMsg")
        assertTrue("Settings should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testRecorder() {
        val commandName = "recorder"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testRecorder: $resultMsg")
        assertTrue("Recorder should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testEshare() {
        val commandName = "eshare"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testEshare: $resultMsg")
        assertTrue("Eshare should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testCamera() {
        val commandName = "camera"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testCamera: $resultMsg")
        assertTrue("Camera should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testScreenshot() {
        val commandName = "screenshot"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testScreenshot: $resultMsg")
        assertTrue("Screenshot should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testFinder() {
        val commandName = "finder"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testFinder: $resultMsg")
        assertTrue("Finder should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testGoBack() {
        val commandName = "go_back"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testGoBack: $resultMsg")
        assertTrue("GoBack should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testNoteMode() {
        val commandName = "note_mode"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testNoteMode: $resultMsg")
        assertTrue("NoteMode should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testWindowMode() {
        val commandName = "window_mode"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testWindowMode: $resultMsg")
        assertTrue("WindowMode should succeed", output.success)
        Thread.sleep(2000)
    }

    @Test
    fun testWifiConnect() {
        val commandName = "wifi_connect"
        showToast("执行命令: $commandName")
        val output = executeCommand(commandName)
        val resultMsg = if (output.success) "✅ 成功: ${output.message}" else "❌ 失败: ${output.message}"
        showToast(resultMsg)
        Log.i(LOG_TAG, "testWifiConnect: $resultMsg")
        assertTrue("WifiConnect should succeed", output.success)
        Thread.sleep(2000)
    }

}