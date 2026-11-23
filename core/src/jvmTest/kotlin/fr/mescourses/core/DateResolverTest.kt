package fr.mescourses.core

import fr.mescourses.core.agent.DateResolver
import fr.mescourses.core.plusDays
import fr.mescourses.core.plusWeeks
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateResolverTest {
    private val samedi = LocalDate(2026, 11, 7)

    @Test fun relatives() {
        assertEquals(samedi, DateResolver.resolve("aujourd'hui", samedi))
        assertEquals(samedi.plusDays(1), DateResolver.resolve("demain", samedi))
        assertEquals(samedi.plusDays(2), DateResolver.resolve("après-demain", samedi))
    }

    @Test fun jours() {
        assertEquals(LocalDate(2026, 11, 9), DateResolver.resolve("lundi", samedi))
        assertEquals(samedi.plusWeeks(1), DateResolver.resolve("samedi prochain", samedi))
    }

    @Test fun formats() {
        assertEquals(LocalDate(2026, 11, 12), DateResolver.resolve("2026-11-12", samedi))
        assertEquals(LocalDate(2026, 11, 12), DateResolver.resolve("12/11", samedi))
        assertEquals(LocalDate(2027, 1, 3), DateResolver.resolve("03/01", samedi))
    }
}
