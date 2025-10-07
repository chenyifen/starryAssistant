package org.stypox.dicio.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import org.dicio.skill.context.SkillContext

/**
 * Get a localized Context using the SkillContext's locale.
 * This ensures that getString() uses the user-selected language, not the system language.
 */
private fun SkillContext.getLocalizedContext(): Context {
    val config = Configuration(this.android.resources.configuration)
    config.setLocale(this.locale)
    return this.android.createConfigurationContext(config)
}

fun SkillContext.getString(@StringRes resId: Int): String {
    return getLocalizedContext().getString(resId)
}

fun SkillContext.getString(@StringRes resId: Int, vararg formatArgs: Any?): String {
    return getLocalizedContext().getString(resId, *formatArgs)
}
