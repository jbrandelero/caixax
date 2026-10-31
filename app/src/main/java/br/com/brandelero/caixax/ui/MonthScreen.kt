package br.com.brandelero.caixax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.brandelero.caixax.data.Category
import br.com.brandelero.caixax.data.Line
import br.com.brandelero.caixax.data.MonthData
import br.com.brandelero.caixax.data.Movement
import br.com.brandelero.caixax.domain.ymOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthScreen() {
    val app = app()
    val hide = app.prefs.hide
    val current = remember { ymOf(YearMonth.now()) }
    var m by rememberSaveable { mutableIntStateOf(current) }
    val data by remember(m) { app.repo.month(m, current) }.collectAsStateWithLifecycle(null)
    val cats by remember { app.repo.dao.categories() }.collectAsStateWithLifecycle(emptyList())
    val places by remember { app.repo.dao.places() }.collectAsStateWithLifecycle(emptyList())
    val catById = cats.associateBy { it.id }
    var editing by remember { mutableStateOf<Line?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showOverdue by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toggle = { l: Line -> scope.launch { app.repo.setPaid(l.entry.id, l.ym, !l.paid) }; Unit }

    Scaffold(
        containerColor = C.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = C.teal, contentColor = Color(0xFF00241B)) {
                Icon(Icons.Rounded.Add, "Novo lançamento")
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { m-- }) { Icon(Icons.Rounded.ChevronLeft, "Mês anterior") }
                    Text(
                        monthTitle(m), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).clickable { m = current }, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { m++ }) { Icon(Icons.Rounded.ChevronRight, "Próximo mês") }
                    EyeButton()
                }
            }
            val d = data ?: return@LazyColumn
            item { Hero(d, hide) }
            if (d.overdue.isNotEmpty()) {
                item { OverdueCard(d.overdue, hide, showOverdue) { showOverdue = !showOverdue } }
                if (showOverdue) items(d.overdue, key = { "o${it.entry.id}-${it.ym}" }) { l ->
                    LineRow(l, catById[l.entry.categoryId], hide, showMonth = true, onToggle = { toggle(l) }, onClick = { editing = l })
                }
            }
            val expenses = d.lines.filter { !it.entry.income }
            if (expenses.isNotEmpty()) item { CategoryChart(expenses, catById, hide) }
            item { SectionTitle("Lançamentos") }
            if (d.lines.isEmpty()) item {
                Text("Nada lançado neste mês. Toque em + para começar.", color = C.muted, modifier = Modifier.padding(vertical = 24.dp))
            }
            items(d.lines, key = { "${it.entry.id}-${it.ym}" }) { l ->
                LineRow(l, catById[l.entry.categoryId], hide, showMonth = false, onToggle = { toggle(l) }, onClick = { editing = l })
            }
            if (d.moves.isNotEmpty()) {
                item { SectionTitle("Guardado no mês") }
                items(d.moves, key = { "m${it.id}" }) { mv -> MoveRow(mv, places.firstOrNull { it.id == mv.placeId }?.name, hide) }
            }
        }
    }

    if (creating || editing != null) {
        EntrySheet(editing, m, cats, onDismiss = { creating = false; editing = null })
    }
}

@Composable
private fun Hero(d: MonthData, hide: Boolean) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.hero).padding(20.dp),
) {
    Text("Saldo previsto", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
    Text(money(d.forecast, hide), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text("Realizado até agora: ${money(d.realized, hide)}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroStat("Receitas", d.income, Icons.Rounded.ArrowDownward, hide, Modifier.weight(1f))
        HeroStat("Despesas", d.expense, Icons.Rounded.ArrowUpward, hide, Modifier.weight(1f))
        if (d.saved != 0L) HeroStat("Guardado", d.saved, Icons.Rounded.Savings, hide, Modifier.weight(1f))
    }
}

@Composable
private fun HeroStat(label: String, cents: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, hide: Boolean, modifier: Modifier) =
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
        Text(money(cents, hide), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

@Composable
private fun OverdueCard(lines: List<Line>, hide: Boolean, expanded: Boolean, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.warn.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(Icons.Rounded.Warning, null, tint = C.warn)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text("${lines.size} em atraso de meses anteriores", color = C.warn, fontWeight = FontWeight.SemiBold)
        val pay = lines.filter { !it.entry.income }.sumOf { it.cents }
        val receive = lines.filter { it.entry.income }.sumOf { it.cents }
        Text(
            listOfNotNull(
                money(pay, hide).takeIf { pay > 0 }?.let { "$it a pagar" },
                money(receive, hide).takeIf { receive > 0 }?.let { "$it a receber" },
            ).joinToString(" · "),
            color = C.muted, style = MaterialTheme.typography.bodySmall,
        )
    }
    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = C.muted)
}

@Composable
private fun CategoryChart(expenses: List<Line>, cats: Map<Long, Category>, hide: Boolean) {
    val byCat = expenses.groupBy { it.entry.categoryId }
        .map { (id, l) -> cats[id] to l.sumOf { it.cents } }
        .sortedByDescending { it.second }
    val total = byCat.sumOf { it.second }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Donut(byCat.map { Color(it.first?.color ?: 0xFF94A3B8) to it.second }, Modifier.size(120.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Despesas", style = MaterialTheme.typography.labelSmall, color = C.muted)
                Text(money(total, hide), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            byCat.take(5).forEach { (cat, cents) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).clip(CircleShape).background(Color(cat?.color ?: 0xFF94A3B8)))
                    Spacer(Modifier.width(8.dp))
                    Text(cat?.name ?: "Sem categoria", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${cents * 100 / total.coerceAtLeast(1)}%", style = MaterialTheme.typography.bodySmall, color = C.muted)
                }
            }
        }
    }
}

@Composable
private fun LineRow(l: Line, cat: Category?, hide: Boolean, showMonth: Boolean, onToggle: () -> Unit, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).clickable(onClick = onClick).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconBubble(categoryIcon(cat?.icon), Color(cat?.color ?: 0xFF94A3B8))
    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(l.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Text(
            listOfNotNull(
                monthShort(l.ym).takeIf { showMonth },
                cat?.name, l.label,
                l.entry.dueDay?.let { "vence dia $it" },
                "recorrente".takeIf { l.entry.count == null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        (if (l.entry.income) "+ " else "− ") + money(l.cents, hide),
        color = if (l.entry.income) C.income else C.expense, fontWeight = FontWeight.SemiBold,
    )
    IconButton(onClick = onToggle) {
        Icon(
            if (l.paid) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (l.paid) "Marcar como pendente" else "Marcar como pago",
            tint = if (l.paid) C.teal else C.muted,
        )
    }
}

@Composable
fun MoveRow(mv: Movement, place: String?, hide: Boolean, onClick: (() -> Unit)? = null) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val deposit = mv.amount > 0
    IconBubble(if (deposit) Icons.Rounded.Savings else Icons.Rounded.Payments, if (deposit) C.blue else C.warn)
    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text("${if (deposit) "Aporte" else "Retirada"} · ${place ?: "?"}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                LocalDate.ofEpochDay(mv.day).format(DateTimeFormatter.ofPattern("dd/MM/yy")),
                "${qty(kotlin.math.abs(mv.amount), hide)} ${mv.symbol}".takeIf { mv.asset != "brl" },
                "afeta o mês".takeIf { mv.affectsMonth },
                mv.note.ifBlank { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    Text((if (deposit) "+ " else "− ") + money(kotlin.math.abs(mv.cents), hide), fontWeight = FontWeight.SemiBold)
}
