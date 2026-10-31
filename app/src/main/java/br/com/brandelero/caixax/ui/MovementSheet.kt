package br.com.brandelero.caixax.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.brandelero.caixax.data.Asset
import br.com.brandelero.caixax.data.CoinGecko
import br.com.brandelero.caixax.data.Movement
import br.com.brandelero.caixax.data.Place
import br.com.brandelero.caixax.data.PresetAssets
import br.com.brandelero.caixax.domain.parseCents
import br.com.brandelero.caixax.domain.parseQty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementSheet(places: List<Place>, quotes: Map<String, Double>, onDismiss: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var deposit by remember { mutableStateOf(true) }
    var placeId by remember { mutableStateOf(places.firstOrNull()?.id) }
    var newPlace by remember { mutableStateOf("") }
    var asset by remember { mutableStateOf(PresetAssets.first()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var amountText by remember { mutableStateOf("") }
    var brlText by remember { mutableStateOf("") }
    var day by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var pickDate by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var affects by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isBrl = asset.id == "brl"
    val price = quotes[asset.id]
    val amount = if (isBrl) parseCents(amountText)?.let { it / 100.0 } else parseQty(amountText)

    // Busca a cotação do ativo escolhido se ainda não estiver no cache
    LaunchedEffect(asset) {
        error = null
        if (!isBrl && price == null) runCatching { app.repo.refreshQuotes(listOf(asset.id)) }
            .onFailure { error = "Não consegui a cotação agora; informe o valor em reais" }
    }
    // Preenche o valor em reais pela cotação do dia (pode ser editado)
    LaunchedEffect(amountText, price) {
        if (!isBrl && amount != null && price != null) brlText = centsText((amount * price * 100).roundToLong())
    }
    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        results = runCatching { CoinGecko.search(query) }.getOrDefault(emptyList())
    }

    val brlCents = if (isBrl) parseCents(amountText) else parseCents(brlText)
    val valid = amount != null && amount > 0 && brlCents != null && (placeId != null || newPlace.isNotBlank())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.surface) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Movimentação", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Segmented(listOf("Aporte", "Retirada"), if (deposit) 0 else 1) { deposit = it == 0 }

            Text("Onde", style = MaterialTheme.typography.labelLarge, color = C.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(places, key = { it.id }) { p ->
                    FilterChip(selected = placeId == p.id, onClick = { placeId = p.id }, label = { Text(p.name) })
                }
                item { FilterChip(selected = placeId == null, onClick = { placeId = null }, label = { Text("+ Novo lugar") }) }
            }
            if (placeId == null) OutlinedTextField(newPlace, { newPlace = it }, label = { Text("Nome (ex.: Nubank, Binance)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Ativo", style = MaterialTheme.typography.labelLarge, color = C.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = if (PresetAssets.any { it.id == asset.id }) PresetAssets else PresetAssets + asset
                items(chips, key = { it.id }) { a ->
                    FilterChip(selected = asset.id == a.id, onClick = { asset = a; searching = false }, label = { Text(a.symbol) })
                }
                item {
                    FilterChip(selected = searching, onClick = { searching = !searching }, label = { Text("Buscar") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) })
                }
            }
            if (searching) {
                OutlinedTextField(query, { query = it }, label = { Text("Nome ou símbolo da cripto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                results.forEach { a ->
                    Text(
                        "${a.symbol} · ${a.name}",
                        modifier = Modifier.fillMaxWidth().clickable { asset = a; searching = false; query = "" }.padding(vertical = 10.dp),
                    )
                }
            }

            OutlinedTextField(
                amountText, { v -> amountText = v.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text(if (isBrl) "Valor em reais" else "Quantidade de ${asset.symbol}") },
                prefix = { if (isBrl) Text("R$ ") },
                supportingText = { if (!isBrl && price != null) Text("1 ${asset.symbol} = ${money((price * 100).roundToLong())}") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            if (!isBrl) OutlinedTextField(
                brlText, { v -> brlText = v.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text("Valor em reais") }, prefix = { Text("R$ ") }, singleLine = true,
                supportingText = { error?.let { Text(it, color = C.warn) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Text("  " + LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            }
            OutlinedTextField(note, { note = it }, label = { Text("Observação (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Afeta o saldo do mês")
                    Text(if (deposit) "Sai do saldo do mês como dinheiro guardado" else "Entra no saldo do mês",
                        style = MaterialTheme.typography.bodySmall, color = C.muted)
                }
                Switch(affects, { affects = it })
            }
            Button(
                onClick = {
                    val sign = if (deposit) 1 else -1
                    val mv = Movement(
                        placeId = placeId ?: 0, asset = asset.id, symbol = asset.symbol, amount = amount!! * sign,
                        cents = brlCents!! * sign, day = day, note = note.trim(), affectsMonth = affects,
                    )
                    scope.launch { app.repo.addMovement(placeId, newPlace, mv); onDismiss() }
                },
                enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.teal, contentColor = Color(0xFF00241B)),
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { day = it / DAY_MS }; pickDate = false }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}
