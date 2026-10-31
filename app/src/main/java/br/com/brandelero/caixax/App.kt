package br.com.brandelero.caixax

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import br.com.brandelero.caixax.data.CaixaDb
import br.com.brandelero.caixax.data.DefaultCategories
import br.com.brandelero.caixax.data.Drive
import br.com.brandelero.caixax.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class App : Application() {
    lateinit var db: CaixaDb
    lateinit var repo: Repo
    lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        db = CaixaDb.open(this)
        repo = Repo(db)
        prefs = Prefs(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (repo.dao.categoryCount() == 0) repo.dao.insertCategories(DefaultCategories)
        }
        if (prefs.drive) Drive.schedule(this)
    }
}

/** Preferências observáveis pelo Compose (mudou, a tela recompõe). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("caixax", Context.MODE_PRIVATE)

    private fun bool(key: String) = object : ReadWriteProperty<Any?, Boolean> {
        val state = mutableStateOf(sp.getBoolean(key, false))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            sp.edit().putBoolean(key, value).apply()
        }
    }

    var hide by bool("hide")
    var lock by bool("lock")
    var secure by bool("secure")
    var drive by bool("drive")

    private val lastBackupState = mutableStateOf(sp.getLong("lastBackup", 0L))
    var lastBackup: Long
        get() = lastBackupState.value
        set(v) {
            lastBackupState.value = v
            sp.edit().putLong("lastBackup", v).apply()
        }
}
