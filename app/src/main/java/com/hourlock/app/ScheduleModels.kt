package com.hourlock.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ScheduleRuleType {
    HOURLY_QUOTA,
    FLAT_ALLOWANCE
}

data class ScheduleBlock(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val ruleType: ScheduleRuleType,
    val limitMinutes: Int
)

data class ScheduleValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

data class ActiveScheduleBlock(
    val block: ScheduleBlock,
    val blockIndex: Int,
    val blockStartMillis: Long,
    val blockEndMillis: Long
)

data class CurrentLimitStatus(
    val limitSeconds: Int,
    val usedSeconds: Int,
    val isBlocked: Boolean,
    val activeBlock: ActiveScheduleBlock,
    val unlockAtMillis: Long
)

fun formatMinuteOfDay(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, 24 * 60)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, normalized / 60)
        set(Calendar.MINUTE, normalized % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(calendar.timeInMillis))
}

fun formatBlockLabel(block: ScheduleBlock): String {
    val start = formatMinuteOfDay(block.startMinuteOfDay)
    val end = formatMinuteOfDay(block.endMinuteOfDay)
    val ruleLabel = when (block.ruleType) {
        ScheduleRuleType.HOURLY_QUOTA -> if (block.limitMinutes == 0) "Locked (0 min)" else "${block.limitMinutes} min/hour"
        ScheduleRuleType.FLAT_ALLOWANCE -> if (block.limitMinutes == 0) "Locked (0 min)" else "${block.limitMinutes} min flat"
    }
    return "$start-$end · $ruleLabel"
}

fun defaultSimpleSchedule(limitMinutes: Int = DEFAULT_LIMIT_MINUTES): List<ScheduleBlock> {
    return listOf(
        ScheduleBlock(
            startMinuteOfDay = 0,
            endMinuteOfDay = 24 * 60,
            ruleType = ScheduleRuleType.HOURLY_QUOTA,
            limitMinutes = limitMinutes.coerceIn(0, 240)
        )
    )
}

fun workFocusPreset(): List<ScheduleBlock> {
    return listOf(
        ScheduleBlock(0, 10 * 60, ScheduleRuleType.HOURLY_QUOTA, 0),
        ScheduleBlock(10 * 60, 13 * 60, ScheduleRuleType.HOURLY_QUOTA, 5),
        ScheduleBlock(13 * 60, 14 * 60, ScheduleRuleType.FLAT_ALLOWANCE, 15),
        ScheduleBlock(14 * 60, 18 * 60, ScheduleRuleType.HOURLY_QUOTA, 8),
        ScheduleBlock(18 * 60, 24 * 60, ScheduleRuleType.HOURLY_QUOTA, 20)
    )
}

fun weekendPreset(): List<ScheduleBlock> {
    return listOf(
        ScheduleBlock(0, 8 * 60, ScheduleRuleType.HOURLY_QUOTA, 0),
        ScheduleBlock(8 * 60, 12 * 60, ScheduleRuleType.HOURLY_QUOTA, 20),
        ScheduleBlock(12 * 60, 16 * 60, ScheduleRuleType.FLAT_ALLOWANCE, 45),
        ScheduleBlock(16 * 60, 24 * 60, ScheduleRuleType.HOURLY_QUOTA, 30)
    )
}
