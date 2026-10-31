package br.com.brandelero.caixax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.brandelero.caixax.data.Holding
import br.com.brandelero.caixax.data.Movement
import br.com.brandelero.caixax.data.evolution
import br.com.brandelero.caixax.data.holdings
import br.com.brandelero.caixax.domain.ymOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen() {
    val app = app()
    val hide = app.prefs.hide
    val places by remember { app.repo.dao.places() }.collectAsStateWithLifecycle(emptyList())
    val moves by remember { app.repo.dao.movements() }.collectAsStateWithLifecycle(emptyList())
    val quoteRows by remember { app.repo.dao.quotes() }.collectAsStateWithLifecycle(emptyList())
    val quotes = quoteRows.associate { it.asset to it.brl }
    var refreshing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Movement?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        refreshing = true
        try {
            app.repo.refreshQuotes()
        } catch (e: Exception) {
            snack.showSnackbar("Sem conexão: usando a última cotação salva")
        } finally {
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val hs = holdings(moves, quotes)
    val total = hs.sumOf { it.cents ?: 0L }
    val curve = evolution(moves, quotes, ymOf(YearMonth.now()))
    val quotedAt = quoteRows.maxOfOrNull { it.at }

    Scaffold(
        containerColor = C.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = C.teal, contentColor = Color(0xFF00241B)) {
                Icon(Icons.Rounded.Add, "Nova movimentação")
            }
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refresh() } }, modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dinheiro guardado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        EyeButton()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.hero).padding(20.dp)) {
                        Text("Patrimônio", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                        Text(money(total, hide), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(
                            quotedAt?.let { "Cotação de " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")) }
                                ?: "Puxe para baixo para atualizar a cotação",
                            color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                        )
                        if (curve.size >= 2 && !hide) {
                            Spacer(Modifier.height(12.dp))
                            Sparkline(curve, Color.White, Modifier.fillMaxWidth().height(56.dp))
                            Text("Evolução mensal a preços de hoje", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                hs.groupBy { it.placeId }.forEach { (placeId, list) ->
                    item(key = "p$placeId") {
                        PlaceCard(places.firstOrNull { it.id == placeId }?.name ?: "?", list, quotes, hide)
                    }
                }
                if (moves.isEmpty()) item {
                    Text("Registre um aporte com o botão + (Nubank, Binance, carteira…).", color = C.muted, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    item { SectionTitle("Movimentações") }
                    items(moves, key = { it.id }) { mv ->
                        MoveRow(mv, places.firstOrNull { it.id == mv.placeId }?.name, hide) { removing = mv }
                    }
                }
            }
        }
    }

    if (adding) MovementSheet(places, quotes) { adding = false }
    removing?.let { mv ->
        ChoiceDialog("Excluir esta movimentação?", listOf("Excluir" to { scope.launch { app.repo.dao.deleteMovement(mv) }; Unit })) { removing = null }
    }
}

@Composable
private fun PlaceCard(name: String, list: List<Holding>, quotes: Map<String, Double>, hide: Boolean) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(16.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(money(list.sumOf { it.cents ?: 0L }, hide), fontWeight = FontWeight.Bold, color = C.teal)
    }
    list.forEach { h ->
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = C.surface2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.symbol, fontWeight = FontWeight.Medium)
                val unit = quotes[h.asset]
                if (h.asset != "brl") Text(
                    "${qty(h.amount, hide)} · " + (unit?.let { money((it * 100).toLong()) + " cada" } ?: "sem cotação"),
                    style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(h.cents?.let { money(it, hide) } ?: "—")
        }
    }
}
