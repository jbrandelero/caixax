package br.com.brandelero.caixax.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Asset(val id: String, val symbol: String, val name: String)

val PresetAssets = listOf(
    Asset("brl", "BRL", "Real"),
    Asset("usd", "USD", "Dólar"),
    Asset("eur", "EUR", "Euro"),
    Asset("bitcoin", "BTC", "Bitcoin"),
    Asset("ethereum", "ETH", "Ethereum"),
    Asset("tether", "USDT", "Tether"),
    Asset("usd-coin", "USDC", "USD Coin"),
)

/** API pública da CoinGecko (sem chave): cripto direto em BRL; dólar e euro via tabela de câmbio. */
object CoinGecko {
    private const val API = "https://api.coingecko.com/api/v3"
    private val FIAT = setOf("usd", "eur")

    suspend fun prices(ids: Collection<String>): Map<String, Double> = withContext(Dispatchers.IO) {
        val out = mutableMapOf<String, Double>()
        val fiat = ids.filter { it in FIAT }
        if (fiat.isNotEmpty()) {
            // Taxas são "unidades por 1 BTC": BRL por USD = brl / usd
            val rates = JSONObject(httpGet("$API/exchange_rates")).getJSONObject("rates")
            val brl = rates.getJSONObject("brl").getDouble("value")
            fiat.forEach { out[it] = brl / rates.getJSONObject(it).getDouble("value") }
        }
        val crypto = ids.filter { it !in FIAT && it != "brl" }
        if (crypto.isNotEmpty()) {
            val j = JSONObject(httpGet("$API/simple/price?vs_currencies=brl&ids=" + URLEncoder.encode(crypto.joinToString(","), "UTF-8")))
            crypto.forEach { id -> j.optJSONObject(id)?.let { out[id] = it.getDouble("brl") } }
        }
        out
    }

    suspend fun search(query: String): List<Asset> = withContext(Dispatchers.IO) {
        val coins = JSONObject(httpGet("$API/search?query=" + URLEncoder.encode(query, "UTF-8"))).getJSONArray("coins")
        (0 until minOf(coins.length(), 15)).map { i ->
            coins.getJSONObject(i).run { Asset(getString("id"), getString("symbol").uppercase(), getString("name")) }
        }
    }
}

/** GET simples; lança exceção em erro HTTP. */
fun httpGet(url: String, token: String? = null): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}
