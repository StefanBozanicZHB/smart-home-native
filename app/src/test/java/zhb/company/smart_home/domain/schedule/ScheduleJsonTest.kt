package zhb.company.smart_home.domain.schedule

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zhb.company.smart_home.domain.garden.Weekday

/** Formats from smart-home-mcu docs/mqtt_topics.md, "API conventions" → "Schedules". */
class ScheduleJsonTest {

    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `times parse and print the way the firmware writes them`() {
        assertEquals(ScheduleTime.Sun(SunEvent.SUNSET, 20), ScheduleTime.parse("sunset+20"))
        assertEquals(ScheduleTime.Sun(SunEvent.SUNRISE, -30), ScheduleTime.parse("sunrise-30"))
        assertEquals(ScheduleTime.Sun(SunEvent.SUNSET, 0), ScheduleTime.parse("sunset"))
        assertEquals(ScheduleTime.Fixed(23 * 60 + 30), ScheduleTime.parse("23:30"))
        assertEquals(ScheduleTime.Fixed(0), ScheduleTime.parse("00:00"))
        listOf("sunset+20", "sunrise-30", "sunset", "sunrise+180", "23:30", "06:05").forEach {
            assertEquals(it, ScheduleTime.parse(it)!!.toWire())
        }
    }

    @Test
    fun `times the firmware rejects are not accepted`() {
        listOf("24:00", "12:60", "6:30", "sunset+181", "sunrise-200", "sunset +20", "dusk", "", null).forEach {
            assertNull(it, ScheduleTime.parse(it))
        }
    }

    @Test
    fun `a schedule object, with and without days`() {
        val s = ScheduleJson.parseSchedule(obj("""{"on":"sunset+20","off":"23:30","days":["sat","sun"]}"""))
        assertEquals(OutputSchedule(ScheduleTime.Sun(SunEvent.SUNSET, 20), ScheduleTime.Fixed(1410), setOf(Weekday.SAT, Weekday.SUN)), s)
        // No days = every day.
        assertEquals(Weekday.entries.toSet(), ScheduleJson.parseSchedule(obj("""{"on":"22:00","off":"sunrise-30"}"""))!!.days)
        assertNull(ScheduleJson.parseSchedule(JsonNull))
        assertNull(ScheduleJson.parseSchedule(null))
        assertNull(ScheduleJson.parseSchedule(obj("""{"on":"later","off":"23:30"}""")))
    }

    @Test
    fun `schedules keep the node's outputs and order, null = manual`() {
        val m = ScheduleJson.parseSchedules(obj("""{"neon":null,"lamp":{"on":"sunset+20","off":"23:30","days":["mon","tue","wed","thu","fri","sat","sun"]}}"""))
        assertEquals(listOf("neon", "lamp"), m.keys.toList())
        assertNull(m["neon"])
        assertEquals(OutputSchedule.DEFAULT, m["lamp"])
    }

    @Test
    fun `today's window`() {
        assertEquals(TodayWindow("16:52", "23:30"), ScheduleJson.parseToday(obj("""{"on":"16:52","off":"23:30"}""")))
        assertNull(ScheduleJson.parseToday(JsonNull))
    }

    @Test
    fun `set command payloads`() {
        assertEquals(
            """{"on":"sunset+20","off":"23:30","days":["mon","tue","wed","thu","fri","sat","sun"],"id":"a"}""",
            ScheduleJson.setCommand("a", OutputSchedule.DEFAULT),
        )
        val weekend = OutputSchedule(ScheduleTime.Fixed(6 * 60 + 5), ScheduleTime.Sun(SunEvent.SUNRISE, -30), setOf(Weekday.SUN, Weekday.SAT))
        assertEquals("""{"on":"06:05","off":"sunrise-30","days":["sat","sun"],"id":"b"}""", ScheduleJson.setCommand("b", weekend))
        assertEquals("null", ScheduleJson.REMOVE)
    }

    @Test
    fun `problems found before sending`() {
        assertNull(ScheduleRules.validate(OutputSchedule.DEFAULT))
        assertEquals(ScheduleProblem.NO_DAYS, ScheduleRules.validate(OutputSchedule.DEFAULT.copy(days = emptySet())))
        assertEquals(ScheduleProblem.OFFSET_RANGE, ScheduleRules.validate(OutputSchedule.DEFAULT.copy(on = ScheduleTime.Sun(SunEvent.SUNSET, 200))))
        assertEquals(ScheduleProblem.SAME_TIMES, ScheduleRules.validate(OutputSchedule.DEFAULT.copy(on = ScheduleTime.Fixed(1410))))
    }
}
