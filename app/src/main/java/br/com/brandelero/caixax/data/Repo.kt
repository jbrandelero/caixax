package br.com.brandelero.caixax.data

import androidx.room.withTransaction
import br.com.brandelero.caixax.domain.Scope
import br.com.brandelero.caixax.domain.ymOfEpochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs
import kotlin.math.roundToLong

/** Uma ocorrência de lançamento num mês, já com as alterações daquele mês aplicadas. */
data class Line(val entry: Entry, val ym: Int, val cents: Long, val description: String, val paid: Boolean, val label: String?)

data class MonthData(val lines: List<Line>, val moves: List<Movement>, val overdue: List<Line>) {
    val income = lines.filter { it.entry.income }.sumOf { it.cents }
    val expense = lines.filter { !it.entry.income }.sumOf { it.cents }
    /** Aporte tira dinheiro do mês, retirada devolve. */
    val saved = moves.sumOf { it.cents }
    val forecast = income - expense - saved
    val realized = lines.filter { it.paid }.sumOf { if (it.entry.income) it.cents else -it.cents } - saved
}

data class Holding(val placeId: Long, val asset: String, val symbol: String, val amount: Double, val cents: Long?)

fun holdings(mv: List<Movement>, quotes: Map<String, Double>): List<Holding> =
    mv.groupBy { it.placeId to it.asset }.map { (k, l) ->
        val amount = l.sumOf { it.amount }
        val price = if (k.second == "brl") 1.0 else quotes[k.second]
        Holding(k.first, k.second, l.first().symbol, amount, price?.let { (amount * it * 100).roundToLong() })
    }.filter { abs(it.amount) > 1e-9 }

/** Patrimônio no fim de cada mês, do primeiro aporte até [current], tudo pela cotação de hoje. */
fun evolution(mv: List<Movement>, quotes: Map<String, Double>, current: Int): List<Long> {
    if (mv.isEmpty()) return emptyList()
    val withYm = mv.map { ymOfEpochDay(it.day) to it }
    val first = withYm.minOf { it.first }
    // shortcut: usa a cotação de hoje para meses passados (não há histórico de preço); buscar /market_chart se quiser valor histórico real
    return (first..current).map { m -> holdings(withYm.filter { it.first <= m }.map { it.second }, quotes).sumOf { it.cents ?: 0L } }
}

class Repo(private val db: CaixaDb) {
    val dao = db.dao()

    private fun line(e: Entry, m: Int, o: Occ?) =
        Line(e, m, o?.cents ?: e.cents, o?.description ?: e.description, o?.paid == true, e.rule().label(m))

    fun month(m: Int, current: Int): Flow<MonthData> = combine(dao.entries(), dao.occs(), dao.movements()) { es, os, mv ->
        val byKey = os.associateBy { it.entryId to it.ym }
        val lines = es.filter { it.rule().activeIn(m) }
            .mapNotNull { e -> byKey[e.id to m].let { o -> if (o?.deleted == true) null else line(e, m, o) } }
            .sortedWith(compareBy({ it.entry.dueDay ?: 99 }, { it.description }))
        val overdue = es.flatMap { e ->
            e.rule().monthsBefore(current).mapNotNull { k ->
                byKey[e.id to k].let { o -> if (o?.deleted == true || o?.paid == true) null else line(e, k, o) }
            }
        }.sortedBy { it.ym }
        MonthData(lines, mv.filter { it.affectsMonth && ymOfEpochDay(it.day) == m }, overdue)
    }

    suspend fun create(e: Entry, paid: Boolean) = db.withTransaction {
        val id = dao.insertEntry(e)
        if (paid) dao.saveOcc(Occ(id, e.start, paid = true))
    }

    suspend fun setPaid(entryId: Long, m: Int, paid: Boolean) {
        val o = dao.occ(entryId, m) ?: Occ(entryId, m)
        dao.saveOcc(o.copy(paid = paid))
    }

    /** [new] traz os campos editados no formulário, com o mesmo id de [old]. */
    suspend fun edit(old: Entry, new: Entry, m: Int, scope: Scope, paid: Boolean) = db.withTransaction {
        val owner = when {
            scope == Scope.THIS && old.count != 1 -> {
                val o = dao.occ(old.id, m) ?: Occ(old.id, m)
                dao.saveOcc(o.copy(cents = new.cents, description = new.description))
                old.id
            }
            scope == Scope.FOLLOWING && m != old.start -> {
                val (before, after) = old.rule().splitAt(m)
                dao.updateEntry(old.copy(count = before!!.count))
                val id = dao.insertEntry(new.copy(id = 0, start = after.start, count = after.count, offset = after.offset, series = after.series))
                dao.moveOccs(old.id, id, m)
                id
            }
            else -> {
                dao.updateEntry(new)
                dao.clearOverrides(old.id)
                old.id
            }
        }
        setPaid(owner, m, paid)
    }

    suspend fun delete(e: Entry, m: Int, scope: Scope) = db.withTransaction {
        when {
            e.count == 1 || scope == Scope.ALL || (scope == Scope.FOLLOWING && m == e.start) -> {
                dao.deleteOccsFrom(e.id, Int.MIN_VALUE)
                dao.deleteEntry(e)
            }
            scope == Scope.THIS -> dao.saveOcc((dao.occ(e.id, m) ?: Occ(e.id, m)).copy(deleted = true))
            else -> {
                dao.updateEntry(e.copy(count = m - e.start))
                dao.deleteOccsFrom(e.id, m)
            }
        }
    }

    suspend fun addMovement(placeId: Long?, newPlace: String, m: Movement) = db.withTransaction {
        val pid = placeId ?: dao.insertPlace(Place(name = newPlace.trim()))
        dao.insertMovement(m.copy(placeId = pid))
    }

    suspend fun refreshQuotes(extra: Collection<String> = emptyList()) {
        val ids = (dao.assets() + extra).toSet() - "brl"
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.saveQuotes(CoinGecko.prices(ids).map { (k, v) -> Quote(k, v, now) })
    }
}
