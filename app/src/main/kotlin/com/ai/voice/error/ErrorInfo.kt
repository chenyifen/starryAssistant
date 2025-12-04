package com.ai.voice.error

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ErrorInfo(val stackTrace: String, val userAction: String = "Unknown") : Parcelable {
    constructor(throwable: Throwable?, userAction: String = "Unknown") : this(
        if (throwable == null) "" else ExceptionUtils.getStackTraceString(throwable),
        userAction
    )
}
