package br.com.brandelero.caixax.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.brandelero.caixax.data.Category
import br.com.brandelero.caixax.data.Drive
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen() {
    val app = app()
    val prefs = app.prefs
    val ctx = LocalContext.current
    val cats by remember { app.repo.dao.categories() }.collectAsStateWithLifecycle(emptyList())
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var editingCat by remember { mutableStateOf<Category?>(null) }
    // Ação que espera o token do Drive (pode passar pela tela de consentimento do Google)
    var pending by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    fun run(token: String) {
        val action = pending ?: return
        pending = null
        scope.launch {
            busy = true
            try {
                action(token)
            } catch (e: Exception) {
                snack.showSnackbar("Falha no Drive: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = runCatching { Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(r.data).accessToken }.getOrNull()
        if (token != null) run(token) else { pending = null; scope.launch { snack.showSnackbar("Autorização do Google cancelada") } }
    }

    fun withDrive(action: suspend (String) -> Unit) {
        pending = action
        scope.launch {
            try {
                val r = Identity.getAuthorizationClient(ctx).authorize(Drive.authRequest).await()
                val intent = r.pendingIntent
                if (r.hasResolution() && intent != null) consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                else run(r.accessToken ?: error("sem token"))
            } catch (e: Exception) {
                pending = null
                snack.showSnackbar("Não foi possível conectar ao Google: ${e.message}")
            }
        }
    }

    val backupNow: suspend (String) -> Unit = { token ->
        Drive.upload(app, token)
        prefs.lastBackup = System.currentTimeMillis()
        snack.showSnackbar("Backup salvo no Google Drive")
    }

    Scaffold(containerColor = C.bg, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) }

            item { SectionTitle("Privacidade") }
            item {
                Card {
                    SwitchRow("Bloqueio com digital ou PIN", "Pede autenticação ao abrir o app", prefs.lock) { on ->
                        val ok = BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
                        if (on && !ok) scope.launch { snack.showSnackbar("Configure um bloqueio de tela no Android primeiro") }
                        else prefs.lock = on
                    }
                    SwitchRow("Ocultar valores", "Mostra R$ •••• no lugar dos valores", prefs.hide) { prefs.hide = it }
                    SwitchRow("Bloquear prints", "Impede capturas de tela e esconde o app nos recentes", prefs.secure) { prefs.secure = it }
                }
            }

            item { SectionTitle("Backup no Google Drive") }
            item {
                Card {
                    Text(
                        when {
                            !prefs.drive -> "Desconectado. Conecte para fazer backup automático diário."
                            prefs.lastBackup == 0L -> "Conectado. Nenhum backup feito ainda."
                            else -> "Conectado. Último backup: " + Instant.ofEpochMilli(prefs.lastBackup).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
                        },
                        color = C.muted, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!prefs.drive) Button(
                        onClick = {
                            withDrive { token ->
                                backupNow(token)
                                prefs.drive = true
                                Drive.schedule(app)
                            }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = C.teal, contentColor = Color(0xFF00241B)),
                    ) { Text("Conectar Google Drive") }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { withDrive(backupNow) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.CloudUpload, null); Text(" Backup agora")
                            }
                            OutlinedButton(onClick = { confirmRestore = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.CloudDownload, null); Text(" Restaurar")
                            }
                        }
                        TextButton(onClick = { prefs.drive = false; Drive.unschedule(app) }) { Text("Desconectar", color = C.muted) }
                    }
                }
            }

            item { SectionTitle("Categorias") }
            items(cats, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).clickable { editingCat = c }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(categoryIcon(c.icon), Color(c.color))
                    Text(c.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Text(if (c.income) "Receita" else "Despesa", color = C.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                TextButton(onClick = { editingCat = Category(name = "", icon = "other", color = Palette.first(), income = false) }) {
                    Icon(Icons.Rounded.Add, null); Text(" Nova categoria")
                }
            }
        }
    }

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("Restaurar backup?") },
        text = { Text("Todos os dados deste aparelho serão substituídos pelo último backup do Drive, e o app será reiniciado.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRestore = false
                withDrive { token ->
                    val file = Drive.download(app, token)
                    if (file == null) snack.showSnackbar("Nenhum backup encontrado no Drive")
                    else Drive.replaceDatabaseAndRestart(app, file)
                }
            }) { Text("Restaurar", color = C.expense) }
        },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } },
        containerColor = C.surface2,
    )

    editingCat?.let { c -> CategoryDialog(c, onDismiss = { editingCat = null }) { saved -> scope.launch { app.repo.dao.saveCategory(saved) }; editingCat = null } }
}

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) { content() }

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = C.muted)
        }
        Switch(checked, onChange)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(initial: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var income by remember { mutableStateOf(initial.income) }
    var color by remember { mutableStateOf(initial.color) }
    var icon by remember { mutableStateOf(initial.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nova categoria" else "Editar categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                Segmented(listOf("Despesa", "Receita"), if (income) 1 else 0) { income = it == 1 }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.forEach { p ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(p))
                                .border(2.dp, if (p == color) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = p },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryIcons.forEach { (key, vector) ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (key == icon) Color(color).copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, key, tint = if (key == icon) Color(color) else C.muted) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(name = name.trim(), income = income, color = color, icon = icon)) }, enabled = name.isNotBlank()) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = C.surface2,
    )
}
