package com.ai.voice.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.dicio.skill.context.SkillContext
import com.ai.voice.di.SkillContextImpl

fun SkillContext.getString(@StringRes resId: Int): String {
    return (this as? SkillContextImpl)?.android?.getString(resId) ?: ""
}

fun SkillContext.getString(@StringRes resId: Int, vararg formatArgs: Any): String {
    return (this as? SkillContextImpl)?.android?.getString(resId, *formatArgs) ?: ""
}

fun checkPermissions(context: Context, vararg permissions: String): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun commaJoinPermissions(context: android.content.Context, permissions: List<org.dicio.skill.skill.Permission>): String {
    return permissions.joinToString(", ") { context.getString(it.name) }
}

