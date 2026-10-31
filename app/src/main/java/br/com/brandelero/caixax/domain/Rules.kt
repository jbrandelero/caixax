package br.com.brandelero.caixax.domain

import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Mês absoluto (ano*12 + mês-1): comparar e somar meses vira aritmética simples. */
fun ymOf(y: YearMonth) = y.year * 12 + y.monthValue - 1
fun ymOf(d: LocalDate) = d.year * 12 + d.monthValue - 1
fun ymOfEpochDay(day: Long) = ymOf(LocalDate.ofEpochDay(day))
fun yearMonth(ym: Int): YearMonth = YearMonth.of(ym / 12, ym % 12 + 1)

enum class Scope { THIS, FOLLOWING, ALL }

/**
 * Regra de um lançamento.
 * count: 1 = único, N = N meses, null = indefinido.
 * offset: meses que ficaram numa regra anterior após "este e próximos" (mantém o rótulo "4/10").
 * series: nasceu com prazo definido (> 1 mês) e por isso mostra rótulo "n/total".
 */
data class Rule(val start: Int, val count: Int?, val offset: Int = 0, val series: Boolean = count != null && count > 1) {
    fun activeIn(m: Int) = m >= start && (count == null || m < start + count)

    fun label(m: Int): String? =
        if (!series || count == null) null else "${m - start + 1 + offset}/${count + offset}"

    /** Meses anteriores a [current] em que a regra esteve ativa (para achar pendências em atraso). */
    fun monthsBefore(current: Int): IntRange =
        start until (if (count == null) current else minOf(current, start + count))

    /** Divide em [m] para "este e próximos": regra que fica (null se some inteira) e regra nova. */
    fun splitAt(m: Int): Pair<Rule?, Rule> {
        require(activeIn(m)) { "mês $m fora da regra" }
        val before = if (m == start) null else copy(count = m - start)
        return before to Rule(m, count?.let { start + it - m }, offset + m - start, series)
    }
}

/** "1.234,56", "12,5", "12" -> centavos. Vazio, inválido ou negativo -> null. */
fun parseCents(s: String): Long? {
    val t = s.trim().removePrefix("R$").trim()
    val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return normalized.toBigDecimalOrNull()
        ?.takeIf { it.signum() >= 0 }
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.movePointRight(2)
        ?.toLong()
}

/** Quantidade de ativo: aceita vírgula ou ponto decimal. */
fun parseQty(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
