package com.ai.voice.io.input.external_popup

class ResultCodeException(resultCode: Int) : Exception("Invalid activity result code: $resultCode")