package br.com.brandelero.caixax.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import br.com.brandelero.caixax.data.Category
import br.com.brandelero.caixax.data.Entry
import br.com.brandelero.caixax.data.Line
import br.com.brandelero.caixax.domain.Scope
import br.com.brandelero.caixax.domain.parseCents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(line: Line?, month: Int, cats: List<Category>, onDismiss: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val e = line?.entry
    var income by remember { mutableStateOf(e?.income ?: false) }
    var amount by remember { mutableStateOf(line?.let { centsText(it.cents) } ?: "") }
    var desc by remember { mutableStateOf(line?.description ?: "") }
    var catId by remember { mutableStateOf(e?.categoryId) }
    var recurring by remember { mutableStateOf(e != null && e.count != 1) }
    var indefinite by remember { mutableStateOf(e != null && e.count == null) }
    var months by remember { mutableStateOf(e?.count?.takeIf { it > 1 }?.toString() ?: "12") }
    var due by remember { mutableStateOf(e?.dueDay?.toString() ?: "") }
    var paid by remember { mutableStateOf(line?.paid ?: false) }
    var dialog by remember { mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null) }

    val visible = cats.filter { it.income == income }
    LaunchedEffect(income, cats) {
        if (visible.none { it.id == catId }) catId = visible.firstOrNull()?.id
    }

    val cents = parseCents(amount)
    val count: Int? = when {
        !recurring -> 1
        indefinite -> null
        else -> months.toIntOrNull()?.takeIf { it in 2..600 }
    }
    val dueDay = due.toIntOrNull()?.takeIf { it in 1..31 }
    val valid = cents != null && cents > 0 && desc.isNotBlank() && catId != null &&
        (!recurring || indefinite || count != null) && (due.isBlank() || dueDay != null)

    fun save() {
        val form = Entry(
            id = e?.id ?: 0, description = desc.trim(), cents = cents!!, income = income, categoryId = catId!!,
            start = e?.start ?: month, count = count, offset = e?.offset ?: 0,
            total = count?.takeIf { it > 1 }, dueDay = dueDay,
        )
        if (e == null || line == null) {
            scope.launch { app.repo.create(form, paid); onDismiss() }
            return
        }
        val recurrenceChanged = count != e.count
        val onlyPaid = !recurrenceChanged && form.description == line.description && form.cents == line.cents &&
            form.income == e.income && form.categoryId == e.categoryId && form.dueDay == e.dueDay
        if (onlyPaid) {
            scope.launch { app.repo.setPaid(e.id, line.ym, paid); onDismiss() }
            return
        }
        // Mudou a recorrência: recomeça a numeração a partir deste mês (ou da série toda, se for o 1º mês)
        val new = if (recurrenceChanged) form.copy(offset = 0) else form.copy(total = e.total)
        val run = { s: Scope -> scope.launch { app.repo.edit(e, new, line.ym, s, paid); onDismiss() }; Unit }
        // "Só este mês" guarda apenas valor e descrição; tipo, categoria e vencimento valem para a série
        val onlyValues = form.income == e.income && form.categoryId == e.categoryId && form.dueDay == e.dueDay
        when {
            e.count == 1 -> run(Scope.ALL)
            recurrenceChanged -> run(if (line.ym == e.start) Scope.ALL else Scope.FOLLOWING)
            else -> dialog = "Aplicar alteração a" to listOfNotNull(
                ("Só este mês" to { run(Scope.THIS) }).takeIf { onlyValues },
                "Este e os próximos" to { run(Scope.FOLLOWING) },
                "Todos os meses" to { run(Scope.ALL) },
            )
        }
    }

    fun delete() {
        if (e == null || line == null) return
        val run = { s: Scope -> scope.launch { app.repo.delete(e, line.ym, s); onDismiss() }; Unit }
        dialog = if (e.count == 1) "Excluir \"${line.description}\"?" to listOf("Excluir" to { run(Scope.ALL) })
        else "Excluir" to listOf(
            "Só este mês" to { run(Scope.THIS) },
            "Este e os próximos" to { run(Scope.FOLLOWING) },
            "Todos os meses" to { run(Scope.ALL) },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (e == null) "Novo lançamento" else "Editar lançamento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Segmented(listOf("Despesa", "Receita"), if (income) 1 else 0) { income = it == 1 }
            OutlinedTextField(
                amount, { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text("Valor") }, prefix = { Text("R$ ") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = if (income) C.income else C.expense),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(desc, { desc = it }, label = { Text("Descrição") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { c ->
                    FilterChip(
                        selected = c.id == catId, onClick = { catId = c.id }, label = { Text(c.name) },
                        leadingIcon = { Icon(categoryIcon(c.icon), null, tint = Color(c.color), modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            Segmented(listOf("Único", "Recorrente"), if (recurring) 1 else 0) { recurring = it == 1 }
            if (recurring) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sem prazo (indefinido)", modifier = Modifier.weight(1f))
                    Switch(indefinite, { indefinite = it })
                }
                if (!indefinite) OutlinedTextField(
                    months, { v -> months = v.filter(Char::isDigit) }, label = { Text("Quantos meses (inclui parcelas)") },
                    singleLine = true, isError = count == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                due, { v -> due = v.filter(Char::isDigit).take(2) }, label = { Text("Dia de vencimento (opcional)") },
                singleLine = true, isError = due.isNotBlank() && dueDay == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(paid, { paid = it })
                Text(if (income) "Recebido neste mês" else "Pago neste mês")
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = ::save, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.teal, contentColor = Color(0xFF00241B)),
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
            if (e != null) TextButton(onClick = ::delete, modifier = Modifier.fillMaxWidth()) { Text("Excluir", color = C.expense) }
        }
    }

    dialog?.let { (title, options) -> ChoiceDialog(title, options) { dialog = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) =
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
