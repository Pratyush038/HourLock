package com.hourlock.app

import android.content.Context
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

data class ScheduleOverride(
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

fun inferDefaultLimitMinutes(blocks: List<ScheduleBlock>): Int {
    if (blocks.isEmpty()) return DEFAULT_LIMIT_MINUTES

    return blocks
        .filter { it.ruleType == ScheduleRuleType.HOURLY_QUOTA }
        .groupBy { it.limitMinutes.coerceIn(0, 240) }
        .maxByOrNull { (_, groupedBlocks) ->
            groupedBlocks.sumOf { block -> block.endMinuteOfDay - block.startMinuteOfDay }
        }
        ?.key
        ?: DEFAULT_LIMIT_MINUTES
}

fun scheduleOverrides(
    blocks: List<ScheduleBlock>,
    defaultLimitMinutes: Int = inferDefaultLimitMinutes(blocks)
): List<ScheduleOverride> {
    val normalizedDefault = defaultLimitMinutes.coerceIn(0, 240)
    val customWindows = blocks
        .sortedBy { it.startMinuteOfDay }
        .filterNot { block ->
            block.ruleType == ScheduleRuleType.HOURLY_QUOTA &&
                block.limitMinutes.coerceIn(0, 240) == normalizedDefault
        }
        .map { block ->
            ScheduleOverride(
                startMinuteOfDay = block.startMinuteOfDay,
                endMinuteOfDay = block.endMinuteOfDay,
                ruleType = block.ruleType,
                limitMinutes = block.limitMinutes.coerceIn(0, 240)
            )
        }

    if (customWindows.size >= 2) {
        val first = customWindows.first()
        val last = customWindows.last()
        if (first.startMinuteOfDay == 0 && last.endMinuteOfDay == 24 * 60 &&
            first.ruleType == last.ruleType && first.limitMinutes == last.limitMinutes
        ) {
            return listOf(
                ScheduleOverride(
                    startMinuteOfDay = last.startMinuteOfDay,
                    endMinuteOfDay = first.endMinuteOfDay,
                    ruleType = first.ruleType,
                    limitMinutes = first.limitMinutes
                )
            ) + customWindows.subList(1, customWindows.lastIndex)
        }
    }

    return customWindows
}

fun buildScheduleFromDefault(
    defaultLimitMinutes: Int = DEFAULT_LIMIT_MINUTES,
    overrides: List<ScheduleOverride>
): List<ScheduleBlock> {
    val defaultLimit = defaultLimitMinutes.coerceIn(0, 240)
    val cleanOverrides = overrides
        .flatMap { override ->
            val start = override.startMinuteOfDay.coerceIn(0, 24 * 60)
            val end = override.endMinuteOfDay.coerceIn(0, 24 * 60)
            val normalized = override.copy(
                startMinuteOfDay = start,
                endMinuteOfDay = end,
                limitMinutes = override.limitMinutes.coerceIn(0, 240)
            )
            when {
                end > start -> listOf(normalized)
                end < start -> listOf(
                    normalized.copy(startMinuteOfDay = 0, endMinuteOfDay = end),
                    normalized.copy(startMinuteOfDay = start, endMinuteOfDay = 24 * 60)
                )
                else -> emptyList()
            }
        }
        .sortedWith(compareBy<ScheduleOverride> { it.startMinuteOfDay }.thenBy { it.endMinuteOfDay })

    val result = mutableListOf<ScheduleBlock>()
    var cursor = 0
    for (override in cleanOverrides) {
        if (override.startMinuteOfDay < cursor) continue

        if (override.startMinuteOfDay > cursor) {
            result += ScheduleBlock(
                startMinuteOfDay = cursor,
                endMinuteOfDay = override.startMinuteOfDay,
                ruleType = ScheduleRuleType.HOURLY_QUOTA,
                limitMinutes = defaultLimit
            )
        }

        result += ScheduleBlock(
            startMinuteOfDay = override.startMinuteOfDay,
            endMinuteOfDay = override.endMinuteOfDay,
            ruleType = override.ruleType,
            limitMinutes = override.limitMinutes
        )
        cursor = override.endMinuteOfDay
    }

    if (cursor < 24 * 60) {
        result += ScheduleBlock(
            startMinuteOfDay = cursor,
            endMinuteOfDay = 24 * 60,
            ruleType = ScheduleRuleType.HOURLY_QUOTA,
            limitMinutes = defaultLimit
        )
    }

    return result.ifEmpty { defaultSimpleSchedule(defaultLimit) }
}

fun scheduleOverrideConflictMessage(overrides: List<ScheduleOverride>): String? {
    if (overrides.any {
            it.startMinuteOfDay.coerceIn(0, 24 * 60) == it.endMinuteOfDay.coerceIn(0, 24 * 60)
        }) {
        return "Choose different start and end times for each custom window."
    }

    val sorted = overrides
        .map {
            it.copy(
                startMinuteOfDay = it.startMinuteOfDay.coerceIn(0, 24 * 60),
                endMinuteOfDay = it.endMinuteOfDay.coerceIn(0, 24 * 60)
            )
        }
        .flatMap { override ->
            if (override.endMinuteOfDay < override.startMinuteOfDay) {
                listOf(
                    override.copy(startMinuteOfDay = 0),
                    override.copy(endMinuteOfDay = 24 * 60)
                )
            } else {
                listOf(override)
            }
        }
        .sortedBy { it.startMinuteOfDay }

    for (override in sorted) {
        if (override.endMinuteOfDay < override.startMinuteOfDay) {
            return "Each custom window needs an end time after its start time."
        }
    }

    for (i in 1 until sorted.size) {
        if (sorted[i].startMinuteOfDay < sorted[i - 1].endMinuteOfDay) {
            return "Custom windows cannot overlap. The rest of the day is filled automatically."
        }
    }

    return null
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

fun formatMinuteOfDay24H(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, 24 * 60)
    val hour = normalized / 60
    val minute = normalized % 60
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

fun formatMinuteOfDayCasual(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, 24 * 60)
    if (normalized == 24 * 60) return "midnight"
    if (normalized == 12 * 60) return "noon"

    val hour24 = normalized / 60
    val minute = normalized % 60
    val suffix = if (hour24 < 12) "AM" else "PM"
    val hour12 = when (val hour = hour24 % 12) {
        0 -> 12
        else -> hour
    }
    return if (minute == 0) {
        "$hour12 $suffix"
    } else {
        String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
    }
}

fun formatBlockDuration(startMinute: Int, endMinute: Int): String {
    val durationMinutes = (endMinute - startMinute).coerceAtLeast(0)
    val h = durationMinutes / 60
    val m = durationMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun splitScheduleBlock(
    blocks: List<ScheduleBlock>,
    index: Int,
    splitMinute: Int? = null
): List<ScheduleBlock> {
    if (blocks.isEmpty()) {
        return defaultSimpleSchedule()
    }
    val targetIndex = if (index in blocks.indices) {
        index
    } else {
        blocks.indices.maxByOrNull { blocks[it].endMinuteOfDay - blocks[it].startMinuteOfDay } ?: 0
    }
    val target = blocks[targetIndex]
    val duration = target.endMinuteOfDay - target.startMinuteOfDay
    if (duration < 2) return blocks

    val midpoint = splitMinute?.coerceIn(target.startMinuteOfDay + 1, target.endMinuteOfDay - 1)
        ?: (target.startMinuteOfDay + duration / 2)

    val first = target.copy(endMinuteOfDay = midpoint)
    val second = target.copy(startMinuteOfDay = midpoint)

    return buildList {
        addAll(blocks.take(targetIndex))
        add(first)
        add(second)
        addAll(blocks.drop(targetIndex + 1))
    }
}

fun deleteScheduleBlock(blocks: List<ScheduleBlock>, index: Int): List<ScheduleBlock> {
    if (index !in blocks.indices) return blocks
    if (blocks.size <= 1) {
        // If deleting the only block, reset to default 24h schedule
        return defaultSimpleSchedule()
    }

    val toRemove = blocks[index]
    return if (index == 0) {
        // First block: extend second block's start to 0
        val next = blocks[1].copy(startMinuteOfDay = toRemove.startMinuteOfDay)
        listOf(next) + blocks.drop(2)
    } else {
        // Subsequent block: extend previous block's end to this block's end
        val prev = blocks[index - 1].copy(endMinuteOfDay = toRemove.endMinuteOfDay)
        blocks.take(index - 1) + listOf(prev) + blocks.drop(index + 1)
    }
}

fun adjustBlockBoundary(
    blocks: List<ScheduleBlock>,
    blockIndex: Int,
    newBoundaryMinute: Int
): List<ScheduleBlock> {
    if (blockIndex !in 0 until blocks.lastIndex) return blocks
    val currentBlock = blocks[blockIndex]
    val nextBlock = blocks[blockIndex + 1]

    val minAllowed = currentBlock.startMinuteOfDay + 1
    val maxAllowed = nextBlock.endMinuteOfDay - 1
    if (minAllowed > maxAllowed) return blocks

    val clamped = newBoundaryMinute.coerceIn(minAllowed, maxAllowed)
    val updatedCurrent = currentBlock.copy(endMinuteOfDay = clamped)
    val updatedNext = nextBlock.copy(startMinuteOfDay = clamped)

    return blocks.mapIndexed { i, block ->
        when (i) {
            blockIndex -> updatedCurrent
            blockIndex + 1 -> updatedNext
            else -> block
        }
    }
}

fun getAppLabel(context: Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

fun formatLockUntil(untilMillis: Long): String {
    if (untilMillis <= 0L) return "-"
    val sdf = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
    return sdf.format(Date(untilMillis))
}
