package org.stypox.dicio.skills.timer

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import org.dicio.skill.chain.OutputGenerator
import org.dicio.skill.output.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.util.StringUtils
import org.stypox.dicio.util.getString
import java.time.Duration

// TODO cleanup this skill and use a service to manage timers
class TimerGenerator : OutputGenerator<TimerGenerator.Data>() {
    enum class Action {
        SET, CANCEL, QUERY // TODO add action to stop playing ringtone, if one is playing
    }

    class Data(val action: Action, val duration: Duration?, val name: String?)

    override fun generate(data: Data): SkillOutput {
        return when (data.action) {
            Action.SET -> {
                if (data.duration == null) {
                    TimerOutput.SetAskDuration { setTimer(it, data.name) }
                } else {
                    setTimer(data.duration, data.name)
                }
            }
            Action.CANCEL -> {
                if (data.name == null && SET_TIMERS.size > 1) {
                    TimerOutput.ConfirmCancel { cancelTimer(null) }
                } else {
                    cancelTimer(data.name)
                }
            }
            Action.QUERY -> queryTimer(data.name)
        }
    }

    private fun setTimer(
        duration: Duration,
        name: String?
    ): SkillOutput {
        var ringtone: Ringtone? = null

        val setTimer = SetTimer(duration, name,
            onMillisTickCallback = { milliseconds ->
                if (milliseconds < 0 && ringtone?.isPlaying == false) {
                    ringtone?.play()
                }
            },
            onSecondsTickCallback = { seconds ->
                if (seconds <= 5) {
                    ctx().speechOutputDevice!!.speak(
                        ctx()
                            .parserFormatter!!.pronounceNumber(seconds.toDouble())
                            .get()
                    )
                }
            },
            onExpiredCallback = { theName ->
                // initialize ringtone when the timer has expired (play will be called right after)
                ringtone = RingtoneManager.getActualDefaultRingtoneUri(
                    ctx().android, RingtoneManager.TYPE_ALARM
                )
                    ?.let {
                        RingtoneManager.getRingtone(ctx().android, it)
                    }
                    ?.also {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // on older API versions it is looped manually in onMillisTickCallback
                            it.isLooping = true
                        }
                        it.play()
                    }

                if (ringtone == null) {
                    // we could not load a ringtone, so we can announce via speech instead
                    ctx().speechOutputDevice!!.speak(
                        formatStringWithName(
                            ctx().android,
                            theName,
                            R.string.skill_timer_expired,
                            R.string.skill_timer_expired_name
                        )
                    )
                }
            },
            onCancelCallback = { timerToCancel ->
                ringtone?.stop()
                ringtone = null
                // removed from setTimers list
                SET_TIMERS.removeIf { setTimer -> setTimer === timerToCancel }
            }
        )

        SET_TIMERS.add(setTimer)

        return TimerOutput.Set(
            duration.toMillis(),
            setTimer.lastTickMillisState,
            name,
        )
    }

    private fun cancelTimer(name: String?): SkillOutput {
        val message: String
        if (SET_TIMERS.isEmpty()) {
            message = ctx().android
                .getString(R.string.skill_timer_no_active)
        } else if (name == null) {
            message = if (SET_TIMERS.size == 1) {
                formatStringWithName(
                    ctx().android,
                    SET_TIMERS[0].name,
                    R.string.skill_timer_canceled,
                    R.string.skill_timer_canceled_name
                )
            } else {
                ctx().getString(R.string.skill_timer_all_canceled)
            }

            // cancel all timers (copying the SET_TIMERS list, since cancel() is going to remove
            // the timer from the SET_TIMERS list, and the for loop would break)
            for (setTimer in SET_TIMERS.toList()) {
                setTimer.cancel()
            }
            if (SET_TIMERS.isNotEmpty()) {
                Log.w(TAG, "Calling cancel() on all timers did not remove them all from the list")
                SET_TIMERS.clear()
            }

        } else {
            val setTimer = getSetTimerWithSimilarName(name)
            if (setTimer == null) {
                message = ctx().android
                    .getString(R.string.skill_timer_no_active_name, name)
            } else {
                message = ctx().android
                    .getString(R.string.skill_timer_canceled_name, setTimer.name)
                setTimer.cancel()
                SET_TIMERS.remove(setTimer)
            }
        }

        return TimerOutput.Cancel(message)
    }

    private fun queryTimer(name: String?): SkillOutput {
        val message = if (SET_TIMERS.isEmpty()) {
            ctx().getString(R.string.skill_timer_no_active)
        } else if (name == null) {
            // no name provided by the user: query the last timer, but adapt the message if only one
            val lastTimer = SET_TIMERS[SET_TIMERS.size - 1]
            @StringRes val noNameQueryString: Int = if (SET_TIMERS.size == 1)
                R.string.skill_timer_query
            else
                R.string.skill_timer_query_last

            formatStringWithName(
                ctx(),
                lastTimer.name,
                lastTimer.lastTickMillis,
                noNameQueryString,
                R.string.skill_timer_query_name
            )

        } else {
            val setTimer = getSetTimerWithSimilarName(name)
            if (setTimer == null) {
                ctx().getString(R.string.skill_timer_no_active_name, name)
            } else {
                ctx().getString(
                    R.string.skill_timer_query_name, setTimer.name,
                    getFormattedDuration(ctx().parserFormatter!!, setTimer.lastTickMillis, true)
                )
            }
        }

        return TimerOutput.Query(message)
    }

    private fun getSetTimerWithSimilarName(name: String): SetTimer? {
        class Pair(val setTimer: SetTimer, val distance: Int)
        return SET_TIMERS
            .mapNotNull { setTimer: SetTimer ->
                setTimer.name?.let { timerName ->
                    Pair(
                        setTimer,
                        StringUtils.customStringDistance(name, timerName)
                    )
                }
            }
            .filter { pair -> pair.distance < 6 }
            .minByOrNull { pair -> pair.distance }
            ?.setTimer
    }

    companion object {
        val SET_TIMERS: MutableList<SetTimer> = ArrayList()
        val TAG: String = TimerGenerator::class.simpleName!!
    }
}
