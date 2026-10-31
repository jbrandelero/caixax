package br.com.brandelero.caixax.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import br.com.brandelero.caixax.domain.Rule
import kotlinx.coroutines.flow.Flow

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    val income: Boolean,
)

/** Regra de lançamento; cada mês em que está ativa gera uma ocorrência. Valores em centavos. */
@Entity
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val cents: Long,
    val income: Boolean,
    val categoryId: Long,
    val start: Int,
    val count: Int?,
    val offset: Int = 0,
    val series: Boolean,
    val dueDay: Int?,
)

fun Entry.rule() = Rule(start, count, offset, series)

/** Estado de uma ocorrência (entrada x mês): pago, excluído ou valor alterado só nesse mês. */
@Entity(
    primaryKeys = ["entryId", "ym"],
    indices = [Index("entryId")],
    foreignKeys = [ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
)
data class Occ(
    val entryId: Long,
    val ym: Int,
    val paid: Boolean = false,
    val deleted: Boolean = false,
    val cents: Long? = null,
    val description: String? = null,
)

@Entity
data class Place(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

/**
 * Aporte (amount > 0) ou retirada (amount < 0) de um ativo num lugar.
 * asset: "brl", "usd", "eur" ou id da CoinGecko. cents: valor em reais no momento, com o mesmo sinal.
 */
@Entity
data class Movement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val asset: String,
    val symbol: String,
    val amount: Double,
    val cents: Long,
    val day: Long,
    val note: String,
    val affectsMonth: Boolean,
)

/** Última cotação conhecida em BRL (cache offline). */
@Entity
data class Quote(@PrimaryKey val asset: String, val brl: Double, val at: Long)

@Dao
interface CaixaDao {
    @Query("SELECT * FROM Category ORDER BY income, name") fun categories(): Flow<List<Category>>
    @Query("SELECT COUNT(*) FROM Category") suspend fun categoryCount(): Int
    @Insert suspend fun insertCategories(c: List<Category>)
    @Upsert suspend fun saveCategory(c: Category)

    @Query("SELECT * FROM Entry") fun entries(): Flow<List<Entry>>
    @Insert suspend fun insertEntry(e: Entry): Long
    @Update suspend fun updateEntry(e: Entry)
    @Delete suspend fun deleteEntry(e: Entry)

    @Query("SELECT * FROM Occ") fun occs(): Flow<List<Occ>>
    @Query("SELECT * FROM Occ WHERE entryId = :id AND ym = :ym") suspend fun occ(id: Long, ym: Int): Occ?
    @Upsert suspend fun saveOcc(o: Occ)
    @Query("UPDATE Occ SET entryId = :to, cents = NULL, description = NULL WHERE entryId = :from AND ym >= :ym")
    suspend fun moveOccs(from: Long, to: Long, ym: Int)
    @Query("UPDATE Occ SET cents = NULL, description = NULL WHERE entryId = :id") suspend fun clearOverrides(id: Long)
    @Query("DELETE FROM Occ WHERE entryId = :id AND ym >= :ym") suspend fun deleteOccsFrom(id: Long, ym: Int)

    @Query("SELECT * FROM Place ORDER BY name") fun places(): Flow<List<Place>>
    @Insert suspend fun insertPlace(p: Place): Long

    @Query("SELECT * FROM Movement ORDER BY day DESC, id DESC") fun movements(): Flow<List<Movement>>
    @Insert suspend fun insertMovement(m: Movement)
    @Delete suspend fun deleteMovement(m: Movement)
    @Query("SELECT DISTINCT asset FROM Movement") suspend fun assets(): List<String>

    @Query("SELECT * FROM Quote") fun quotes(): Flow<List<Quote>>
    @Upsert suspend fun saveQuotes(q: List<Quote>)
}

@Database(entities = [Category::class, Entry::class, Occ::class, Place::class, Movement::class, Quote::class], version = 1)
abstract class CaixaDb : RoomDatabase() {
    abstract fun dao(): CaixaDao

    companion object {
        const val NAME = "caixax.db"
        fun open(ctx: Context) = Room.databaseBuilder(ctx, CaixaDb::class.java, NAME).build()
    }
}

val DefaultCategories = listOf(
    Category(name = "Moradia", icon = "home", color = 0xFF3B82F6, income = false),
    Category(name = "Mercado", icon = "cart", color = 0xFF00D1A0, income = false),
    Category(name = "Alimentação", icon = "food", color = 0xFFFB923C, income = false),
    Category(name = "Transporte", icon = "car", color = 0xFFFFB547, income = false),
    Category(name = "Saúde", icon = "health", color = 0xFFFF6B7A, income = false),
    Category(name = "Educação", icon = "school", color = 0xFFA78BFA, income = false),
    Category(name = "Contas", icon = "bolt", color = 0xFF22D3EE, income = false),
    Category(name = "Internet e celular", icon = "wifi", color = 0xFF60A5FA, income = false),
    Category(name = "Lazer", icon = "fun", color = 0xFFF472B6, income = false),
    Category(name = "Pets", icon = "pets", color = 0xFF84CC16, income = false),
    Category(name = "Roupas", icon = "shirt", color = 0xFFE879F9, income = false),
    Category(name = "Outros", icon = "other", color = 0xFF94A3B8, income = false),
    Category(name = "Salário", icon = "work", color = 0xFF00D1A0, income = true),
    Category(name = "Pró-labore", icon = "money", color = 0xFF3B82F6, income = true),
    Category(name = "Extras", icon = "star", color = 0xFFFFB547, income = true),
    Category(name = "Outras receitas", icon = "gift", color = 0xFF94A3B8, income = true),
)
