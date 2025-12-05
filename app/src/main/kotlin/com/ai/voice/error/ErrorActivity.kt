package com.ai.voice.error

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import com.ai.voice.BuildConfig
import com.ai.voice.R
import com.ai.voice.ui.theme.AppTheme
import com.ai.voice.util.ShareUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class ErrorActivity : ComponentActivity() {

    private lateinit var errorInfo: ErrorInfo
    private lateinit var locale: Locale
    private lateinit var currentTimeStamp: String
    private lateinit var osInfo: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        errorInfo = IntentCompat.getParcelableExtra(intent, ERROR_INFO, ErrorInfo::class.java)
            ?: ErrorInfo(Exception("Could not get ErrorInfo from intent extras"))
        Log.e(TAG, errorInfo.stackTrace)

        locale = Locale.getDefault()
        currentTimeStamp = CURRENT_TIMESTAMP_FORMATTER.format(LocalDateTime.now())
        osInfo = getOsInfo()

        setContent {
            AppTheme {
            ErrorScreen(
                errorInfo = errorInfo,
                locale = locale,
                timestamp = currentTimeStamp,
                osInfo = osInfo,
                onCopy = {
                    ShareUtils.copyToClipboard(this, buildMarkdown())
                },
                onShare = {
                    ShareUtils.shareText(this, getString(R.string.error_title), buildMarkdown())
                },
                onReport = {
                    ShareUtils.openUrlInBrowser(this, ERROR_GITHUB_ISSUE_URL, false)
                },
                onBack = {
                    finish()
                },
            )
            }
        }
    }

    private fun buildMarkdown(): String {
        return try {
            val htmlErrorReport = StringBuilder()

            htmlErrorReport
                .append("## Exception")
                .append("\n* __User action:__ ")
                .append(errorInfo.userAction)
                .append("\n* __App locale:__ ").append(locale.toString())
                .append("\n* __Version:__ ").append(BuildConfig.VERSION_NAME)
                .append("\n* __OS:__ ").append(osInfo).append("\n")

            if (errorInfo.stackTrace.isNotEmpty()) {
                htmlErrorReport
                    .append("<details><summary><b>Crash log</b></summary><p>\n")
                    .append("\n```\n")
                    .append(errorInfo.stackTrace)
                    .append("\n```\n")
                    .append("</details>\n")
            }
            htmlErrorReport.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Could not build markdown", e)
            ""
        }
    }

    companion object {
        val TAG: String = ErrorActivity::class.simpleName!!
        const val ERROR_INFO = "error_info"
        const val ERROR_GITHUB_ISSUE_URL = "https://github.com/Stypox/dicio-android/issues"
        val CURRENT_TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private fun getOsInfo(): String {
            val osBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Build.VERSION.BASE_OS
            else
                "Android"

            return ((System.getProperty("os.name") ?: "")
                    + " " + osBase.ifEmpty { "Android" }
                    + " " + Build.VERSION.RELEASE
                    + " - " + Build.VERSION.SDK_INT)
        }
    }
}
