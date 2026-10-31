package br.com.brandelero.caixax.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class RulesTest {
    private val jan = ymOf(YearMonth.of(2026, 1))

    @Test fun parcelado10x() {
        val r = Rule(jan, 10)
        assertTrue(r.activeIn(jan + 9))
        assertFalse(r.activeIn(jan + 10))
        assertFalse(r.activeIn(jan - 1))
        assertEquals("4/10", r.label(jan + 3))
    }

    @Test fun unicoNaoTemRotulo() {
        val r = Rule(jan, 1)
        assertNull(r.label(jan))
        assertFalse(r.activeIn(jan + 1))
    }

    @Test fun esteEProximosMantemNumeracao() {
        val (before, after) = Rule(jan, 10).splitAt(jan + 3)
        assertEquals(Rule(jan, 3, 0, true), before)
        assertEquals("4/10", after.label(jan + 3))
        assertEquals("10/10", after.label(jan + 9))
        assertFalse(after.activeIn(jan + 10))
    }

    @Test fun indefinidoDivididoNaoGanhaRotulo() {
        val (before, after) = Rule(jan, null).splitAt(jan + 3)
        assertNull(before!!.label(jan))
        assertNull(after.count)
        assertTrue(after.activeIn(jan + 100))
    }

    @Test fun dividirNoInicioNaoDeixaRegraAntiga() {
        assertNull(Rule(jan, 5).splitAt(jan).first)
    }

    @Test fun mesesEmAtraso() {
        assertEquals(jan until jan + 3, Rule(jan, null).monthsBefore(jan + 3))
        assertEquals(jan until jan + 2, Rule(jan, 2).monthsBefore(jan + 5))
        assertTrue(Rule(jan + 1, null).monthsBefore(jan).isEmpty())
    }

    @Test fun valores() {
        assertEquals(123456L, parseCents("1.234,56"))
        assertEquals(1250L, parseCents("12,5"))
        assertEquals(1200L, parseCents("12"))
        assertEquals(1999L, parseCents("R$ 19,99"))
        assertNull(parseCents("abc"))
        assertNull(parseCents("-3"))
        assertNull(parseCents(""))
        assertEquals(0.0123, parseQty("0,0123")!!, 1e-12)
        assertNull(parseQty("0"))
    }

    @Test fun mesAbsoluto() {
        assertEquals(YearMonth.of(2026, 12), yearMonth(ymOf(YearMonth.of(2026, 12))))
        assertEquals(YearMonth.of(2027, 1), yearMonth(ymOf(YearMonth.of(2026, 12)) + 1))
    }
}
