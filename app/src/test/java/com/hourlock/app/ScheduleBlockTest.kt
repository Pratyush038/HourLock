package com.hourlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBlockTest {

    private fun validate(blocks: List<ScheduleBlock>): Boolean {
        if (blocks.isEmpty()) return false
        val sorted = blocks.sortedBy { it.startMinuteOfDay }
        if (sorted.first().startMinuteOfDay != 0) return false

        var expectedStart = 0
        for (block in sorted) {
            if (block.startMinuteOfDay != expectedStart) return false
            if (block.endMinuteOfDay <= block.startMinuteOfDay) return false
            if (block.endMinuteOfDay > 24 * 60) return false
            if (block.limitMinutes < 0) return false
            expectedStart = block.endMinuteOfDay
        }
        return expectedStart == 24 * 60
    }

    @Test
    fun testDefaultSimpleScheduleIsValid() {
        val schedule = defaultSimpleSchedule()
        assertEquals(1, schedule.size)
        assertEquals(0, schedule[0].startMinuteOfDay)
        assertEquals(24 * 60, schedule[0].endMinuteOfDay)
        assertTrue(validate(schedule))
    }

    @Test
    fun testPresetsAreValid() {
        assertTrue(validate(workFocusPreset()))
        assertTrue(validate(weekendPreset()))
    }

    @Test
    fun testSplitBlock() {
        val simple = defaultSimpleSchedule()
        val split = splitScheduleBlock(simple, 0)
        assertEquals(2, split.size)
        assertEquals(0, split[0].startMinuteOfDay)
        assertEquals(12 * 60, split[0].endMinuteOfDay)
        assertEquals(12 * 60, split[1].startMinuteOfDay)
        assertEquals(24 * 60, split[1].endMinuteOfDay)
        assertTrue(validate(split))
    }

    @Test
    fun testSplitEmptyScheduleReturnsDefault() {
        val split = splitScheduleBlock(emptyList(), 0)
        assertEquals(1, split.size)
        assertTrue(validate(split))
    }

    @Test
    fun testDeleteBlockMergesCleanlyWithoutGaps() {
        val preset = workFocusPreset() // 5 blocks
        assertEquals(5, preset.size)

        // Delete middle block (index 2)
        val deletedMiddle = deleteScheduleBlock(preset, 2)
        assertEquals(4, deletedMiddle.size)
        assertTrue(validate(deletedMiddle))

        // Delete first block (index 0)
        val deletedFirst = deleteScheduleBlock(preset, 0)
        assertEquals(4, deletedFirst.size)
        assertEquals(0, deletedFirst[0].startMinuteOfDay)
        assertTrue(validate(deletedFirst))

        // Delete last block (index 4)
        val deletedLast = deleteScheduleBlock(preset, 4)
        assertEquals(4, deletedLast.size)
        assertEquals(24 * 60, deletedLast.last().endMinuteOfDay)
        assertTrue(validate(deletedLast))

        // Delete only block resets to default
        val single = defaultSimpleSchedule()
        val deletedSingle = deleteScheduleBlock(single, 0)
        assertEquals(1, deletedSingle.size)
        assertTrue(validate(deletedSingle))
    }

    @Test
    fun testAdjustBlockBoundaryMaintainsContiguity() {
        val schedule = defaultSimpleSchedule()
        val split = splitScheduleBlock(schedule, 0) // [0..720], [720..1440]
        
        // Move boundary from 720 (12:00 PM) to 600 (10:00 AM)
        val adjusted = adjustBlockBoundary(split, 0, 600)
        assertEquals(2, adjusted.size)
        assertEquals(0, adjusted[0].startMinuteOfDay)
        assertEquals(600, adjusted[0].endMinuteOfDay)
        assertEquals(600, adjusted[1].startMinuteOfDay)
        assertEquals(1440, adjusted[1].endMinuteOfDay)
        assertTrue(validate(adjusted))
    }

    @Test
    fun testSplitInvalidIndexFallsBackToLargestBlock() {
        val schedule = listOf(
            ScheduleBlock(0, 60, ScheduleRuleType.HOURLY_QUOTA, 5),
            ScheduleBlock(60, 24 * 60, ScheduleRuleType.FLAT_ALLOWANCE, 30)
        )

        val split = splitScheduleBlock(schedule, index = 99)

        assertEquals(3, split.size)
        assertEquals(60, split[1].startMinuteOfDay)
        assertEquals(750, split[1].endMinuteOfDay)
        assertEquals(750, split[2].startMinuteOfDay)
        assertEquals(24 * 60, split[2].endMinuteOfDay)
        assertTrue(validate(split))
    }

    @Test
    fun testSplitMinuteIsClampedInsideTargetBlock() {
        val split = splitScheduleBlock(defaultSimpleSchedule(), index = 0, splitMinute = -50)

        assertEquals(2, split.size)
        assertEquals(1, split[0].endMinuteOfDay)
        assertEquals(1, split[1].startMinuteOfDay)
        assertTrue(validate(split))
    }

    @Test
    fun testOneMinuteBlockCannotBeSplit() {
        val schedule = listOf(
            ScheduleBlock(0, 1, ScheduleRuleType.HOURLY_QUOTA, 5),
            ScheduleBlock(1, 24 * 60, ScheduleRuleType.HOURLY_QUOTA, 5)
        )

        assertEquals(schedule, splitScheduleBlock(schedule, 0))
    }

    @Test
    fun testDeleteInvalidIndexLeavesScheduleUnchanged() {
        val schedule = workFocusPreset()

        assertEquals(schedule, deleteScheduleBlock(schedule, -1))
        assertEquals(schedule, deleteScheduleBlock(schedule, schedule.size))
    }

    @Test
    fun testAdjustBoundaryClampsToOneMinuteMinimumBlocks() {
        val split = splitScheduleBlock(defaultSimpleSchedule(), 0)

        val atStart = adjustBlockBoundary(split, 0, -100)
        assertEquals(1, atStart[0].endMinuteOfDay)
        assertEquals(1, atStart[1].startMinuteOfDay)

        val atEnd = adjustBlockBoundary(split, 0, 24 * 60 + 100)
        assertEquals(24 * 60 - 1, atEnd[0].endMinuteOfDay)
        assertEquals(24 * 60 - 1, atEnd[1].startMinuteOfDay)

        assertTrue(validate(atStart))
        assertTrue(validate(atEnd))
    }

    @Test
    fun testAdjustInvalidBoundaryIndexLeavesScheduleUnchanged() {
        val split = splitScheduleBlock(defaultSimpleSchedule(), 0)

        assertEquals(split, adjustBlockBoundary(split, -1, 100))
        assertEquals(split, adjustBlockBoundary(split, split.lastIndex, 100))
    }

    @Test
    fun testDefaultLimitIsClampedToSupportedRange() {
        assertEquals(0, defaultSimpleSchedule(-10).single().limitMinutes)
        assertEquals(240, defaultSimpleSchedule(999).single().limitMinutes)
    }

    @Test
    fun testBuildScheduleFromDefaultFillsUnspecifiedTime() {
        val schedule = buildScheduleFromDefault(
            defaultLimitMinutes = 10,
            overrides = listOf(
                ScheduleOverride(9 * 60, 17 * 60, ScheduleRuleType.HOURLY_QUOTA, 0)
            )
        )

        assertEquals(3, schedule.size)
        assertEquals(ScheduleBlock(0, 9 * 60, ScheduleRuleType.HOURLY_QUOTA, 10), schedule[0])
        assertEquals(ScheduleBlock(9 * 60, 17 * 60, ScheduleRuleType.HOURLY_QUOTA, 0), schedule[1])
        assertEquals(ScheduleBlock(17 * 60, 24 * 60, ScheduleRuleType.HOURLY_QUOTA, 10), schedule[2])
        assertTrue(validate(schedule))
    }

    @Test
    fun testBuildScheduleFromDefaultSupportsMultipleExceptionWindows() {
        val schedule = buildScheduleFromDefault(
            defaultLimitMinutes = 10,
            overrides = listOf(
                ScheduleOverride(22 * 60, 24 * 60, ScheduleRuleType.HOURLY_QUOTA, 0),
                ScheduleOverride(12 * 60, 13 * 60, ScheduleRuleType.FLAT_ALLOWANCE, 30)
            )
        )

        assertEquals(4, schedule.size)
        assertEquals(ScheduleBlock(0, 12 * 60, ScheduleRuleType.HOURLY_QUOTA, 10), schedule[0])
        assertEquals(ScheduleBlock(12 * 60, 13 * 60, ScheduleRuleType.FLAT_ALLOWANCE, 30), schedule[1])
        assertEquals(ScheduleBlock(13 * 60, 22 * 60, ScheduleRuleType.HOURLY_QUOTA, 10), schedule[2])
        assertEquals(ScheduleBlock(22 * 60, 24 * 60, ScheduleRuleType.HOURLY_QUOTA, 0), schedule[3])
        assertTrue(validate(schedule))
    }

    @Test
    fun testScheduleOverrideConflictMessageAllowsGapsButRejectsOverlaps() {
        val gaps = listOf(
            ScheduleOverride(8 * 60, 9 * 60, ScheduleRuleType.HOURLY_QUOTA, 0),
            ScheduleOverride(17 * 60, 18 * 60, ScheduleRuleType.HOURLY_QUOTA, 5)
        )
        val overlaps = listOf(
            ScheduleOverride(8 * 60, 10 * 60, ScheduleRuleType.HOURLY_QUOTA, 0),
            ScheduleOverride(9 * 60, 11 * 60, ScheduleRuleType.HOURLY_QUOTA, 5)
        )

        assertEquals(null, scheduleOverrideConflictMessage(gaps))
        assertTrue(scheduleOverrideConflictMessage(overlaps)?.contains("overlap") == true)
    }

    @Test
    fun testInferDefaultLimitUsesMostCoveredHourlyLimit() {
        val schedule = buildScheduleFromDefault(
            defaultLimitMinutes = 10,
            overrides = listOf(
                ScheduleOverride(9 * 60, 17 * 60, ScheduleRuleType.HOURLY_QUOTA, 0)
            )
        )

        assertEquals(10, inferDefaultLimitMinutes(schedule))
        assertEquals(1, scheduleOverrides(schedule, defaultLimitMinutes = 10).size)
    }

    @Test
    fun testFormatHelpersHandleEdges() {
        assertEquals("00:00", formatMinuteOfDay24H(-1))
        assertEquals("24:00", formatMinuteOfDay24H(24 * 60 + 1))
        assertEquals("12 AM", formatMinuteOfDayCasual(0))
        assertEquals("10 PM", formatMinuteOfDayCasual(22 * 60))
        assertEquals("10:30 PM", formatMinuteOfDayCasual(22 * 60 + 30))
        assertEquals("noon", formatMinuteOfDayCasual(12 * 60))
        assertEquals("midnight", formatMinuteOfDayCasual(24 * 60))
        assertEquals("0m", formatBlockDuration(60, 30))
        assertEquals("1h 15m", formatBlockDuration(0, 75))
    }
}
