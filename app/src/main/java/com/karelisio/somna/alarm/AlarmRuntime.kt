package com.karelisio.somna.alarm

import kotlinx.coroutines.flow.MutableStateFlow

/** Live state of the alarm currently in progress, shared between [AlarmService] and [AlarmActivity]. */
data class AlarmSession(
    val spec: AlarmSpec,
    val sunriseStart: Long? = null,
    val smartActive: Boolean = false,
    val ringing: Boolean = false,
    val ringStartedAt: Long = 0L,
    val smartTriggered: Boolean = false,
) {
    val sunriseEnd: Long get() = spec.triggerAt
}

sealed interface AlarmOutcome {
    val spec: AlarmSpec
    data class Dismissed(override val spec: AlarmSpec, val at: Long) : AlarmOutcome
    data class Snoozed(override val spec: AlarmSpec, val until: Long) : AlarmOutcome
    data class TimedOut(override val spec: AlarmSpec) : AlarmOutcome
}

object AlarmRuntime {
    val session = MutableStateFlow<AlarmSession?>(null)
    val outcome = MutableStateFlow<AlarmOutcome?>(null)
    /** Current loudness of the ringing sound (0..1), for the pulsing UI. */
    @Volatile var level: Float = 0f
}
