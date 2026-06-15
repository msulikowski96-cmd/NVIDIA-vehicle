package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiGeocodingService {
    private const val TAG = "GeminiGeocoding"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Offline database of main coordinates of Polish cities for ultimate fallback
    private val offlineCities = mapOf(
        "warszawa" to Pair(52.2297, 21.0122),
        "krakow" to Pair(50.0647, 19.9450),
        "kraków" to Pair(50.0647, 19.9450),
        "wroclaw" to Pair(51.1079, 17.0385),
        "wrocław" to Pair(51.1079, 17.0385),
        "poznan" to Pair(52.4069, 16.9299),
        "poznań" to Pair(52.4069, 16.9299),
        "gdansk" to Pair(54.3520, 18.6466),
        "gdańsk" to Pair(54.3520, 18.6466),
        "lodz" to Pair(51.7592, 19.4560),
        "łódź" to Pair(51.7592, 19.4560),
        "szczecin" to Pair(53.4285, 14.5528),
        "bydgoszcz" to Pair(53.1235, 18.0084),
        "lublin" to Pair(51.2465, 22.5684),
        "katowice" to Pair(50.2649, 19.0216),
        "bialystok" to Pair(53.1325, 23.1688),
        "białystok" to Pair(53.1325, 23.1688),
        "gdynia" to Pair(54.5189, 18.5305),
        "czestochowa" to Pair(50.8118, 19.1203),
        "częstochowa" to Pair(50.8118, 19.1203),
        "radom" to Pair(51.4027, 21.1471),
        "sosnowiec" to Pair(50.2863, 19.1041),
        "torun" to Pair(53.0138, 18.5984),
        "toruń" to Pair(53.0138, 18.5984),
        "rzeszow" to Pair(50.0412, 21.9991),
        "rzeszów" to Pair(50.0412, 21.9991),
        "kielce" to Pair(50.8661, 20.6286),
        "gliwice" to Pair(50.2940, 18.6714),
        "olsztyn" to Pair(53.7784, 20.4801),
        "zabrze" to Pair(50.3246, 18.7732),
        "bielsko-biala" to Pair(49.8225, 19.0444),
        "bielsko-biała" to Pair(49.8225, 19.0444),
        "zakopane" to Pair(49.2992, 19.9495),
        "nowy sacz" to Pair(49.6218, 20.6971),
        "nowy sącz" to Pair(49.6218, 20.6971),
        "oswiecim" to Pair(50.0344, 19.2098),
        "oświęcim" to Pair(50.0344, 19.2098),
        "sopot" to Pair(54.4418, 18.5601)
    )

    /**
     * Parses custom natural language route using Gemini API or Local Fallback.
     * Returns: list containing start route, followed by intermediate stops, ending with target destination.
     */
    suspend fun parseNaturalLanguageRoute(prompt: String): ParseRouteResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "No Gemini API key. Running offline/local dictionary parser.")
            return@withContext parseOfflineRoute(prompt)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val systemInstruction = "Jesteś asystentem ds. logistyki i geolokalizacji. Twoim zadaniem jest parsowanie tekstu polskiego użytkownika opisującego podróż (np. 'chcę wyjechać z Warszawy, odwiedzić Łódź i Wrocław, skończyć w Krakowie'). Musisz rozpoznać:\n" +
                "1. Punkt startowy (startPoint): Nazwa miasta i jego współrzędne (szerokość i długość geograficzna).\n" +
                "2. Punkt końcowy (endPoint): Nazwa miasta i jego współrzędne.\n" +
                "3. Punkty pośrednie (intermediateStops): Lista miast, które chce odwiedzić po drodze wraz ze współrzędnymi.\n\n" +
                "Zweryfikuj poprawność geograficzną miast (np. dla miast w Polsce podaj rzeczywiste koordynaty). Zwróć dane w formacie czystego JSON o strukturze:\n" +
                "{\n" +
                "  \"startPoint\": {\"name\": \"Kraków\", \"latitude\": 50.0647, \"longitude\": 19.9450},\n" +
                "  \"endPoint\": {\"name\": \"Katowice\", \"latitude\": 50.2649, \"longitude\": 19.0216},\n" +
                "  \"intermediateStops\": [\n" +
                "    {\"name\": \"Zakopane\", \"latitude\": 49.2992, \"longitude\": 19.9495},\n" +
                "    {\"name\": \"Nowy Sącz\", \"latitude\": 49.6218, \"longitude\": 20.6971}\n" +
                "  ]\n" +
                "}\n" +
                "Zwróć TYLKO czysty JSON bez markdownu ```json czy innych tekstów."

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Przetwarzaj ten opis trasy: \"$prompt\"")
                        })
                    })
                })
            })
            // Set System Instruction
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            // Configure JSON response format
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed with code: ${response.code}")
                return@withContext parseOfflineRoute(prompt).copy(isOffline = true, errorMsg = "Błąd API Gemini (${response.code}). Pobrano z lokalnego słownika.")
            }

            val body = response.body?.string() ?: ""
            Log.d(TAG, "Gemini raw response: $body")

            // Parse response
            val rootObj = JSONObject(body)
            val candidates = rootObj.getJSONArray("candidates")
            val rawText = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val routeObj = JSONObject(rawText.trim())
            
            val startJs = routeObj.getJSONObject("startPoint")
            val startStop = Stop(UUID.randomUUID().toString(), startJs.getString("name"), startJs.getDouble("latitude"), startJs.getDouble("longitude"))

            val endJs = routeObj.getJSONObject("endPoint")
            val endStop = Stop(UUID.randomUUID().toString(), endJs.getString("name"), endJs.getDouble("latitude"), endJs.getDouble("longitude"))

            val stopsList = mutableListOf<Stop>()
            if (routeObj.has("intermediateStops")) {
                val stopsArr = routeObj.getJSONArray("intermediateStops")
                for (i in 0 until stopsArr.length()) {
                    val js = stopsArr.getJSONObject(i)
                    stopsList.add(Stop(UUID.randomUUID().toString(), js.getString("name"), js.getDouble("latitude"), js.getDouble("longitude")))
                }
            }

            ParseRouteResult(startStop, endStop, stopsList, isOffline = false)
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing route with Gemini", e)
            parseOfflineRoute(prompt).copy(isOffline = true, errorMsg = "Wyjątek API: ${e.localizedMessage}. Pobrano z lokalnego słownika.")
        }
    }

    /**
     * Offline local fallback using word recognition from user prompt
     */
    private fun parseOfflineRoute(prompt: String): ParseRouteResult {
        val normalized = prompt.lowercase()
        val foundCities = mutableListOf<Pair<String, Pair<Double, Double>>>()

        // Match offline city keywords inside the prompt
        for ((cityName, coords) in offlineCities) {
            if (normalized.contains(cityName)) {
                if (foundCities.none { it.first == cityName }) {
                    foundCities.add(Pair(cityName.replaceFirstChar { it.uppercase() }, coords))
                }
            }
        }

        // Setup some nice defaults if searching found nothing
        if (foundCities.isEmpty()) {
            return ParseRouteResult(
                startPoint = Stop(UUID.randomUUID().toString(), "Warszawa", 52.2297, 21.0122),
                endPoint = Stop(UUID.randomUUID().toString(), "Kraków", 50.0647, 19.9450),
                intermediateStops = listOf(
                    Stop(UUID.randomUUID().toString(), "Katowice", 50.2649, 19.0216),
                    Stop(UUID.randomUUID().toString(), "Wrocław", 51.1079, 17.0385)
                ),
                isOffline = true,
                errorMsg = "Nie wykryto miast słownikowych w Twoim tekście. Załadowano przykładową trasę."
            )
        }

        // Logic to assign Start, End and Stops
        val startPoint: Stop
        val endPoint: Stop
        val intermediateStops = mutableListOf<Stop>()

        if (foundCities.size == 1) {
            // Only 1 matched city
            val (name, coords) = foundCities[0]
            startPoint = Stop(UUID.randomUUID().toString(), name, coords.first, coords.second)
            endPoint = Stop(UUID.randomUUID().toString(), "Kraków", 50.0647, 19.9450)
            intermediateStops.add(Stop(UUID.randomUUID().toString(), "Katowice", 50.2649, 19.0216))
        } else if (foundCities.size == 2) {
            val (name1, coords1) = foundCities[0]
            val (name2, coords2) = foundCities[1]
            startPoint = Stop(UUID.randomUUID().toString(), name1, coords1.first, coords1.second)
            endPoint = Stop(UUID.randomUUID().toString(), name2, coords2.first, coords2.second)
            intermediateStops.add(Stop(UUID.randomUUID().toString(), "Częstochowa", 50.8118, 19.1203))
        } else {
            // First is start, last is end, matching rest as stops
            val (nameStart, coordsStart) = foundCities.first()
            val (nameEnd, coordsEnd) = foundCities.last()
            startPoint = Stop(UUID.randomUUID().toString(), nameStart, coordsStart.first, coordsStart.second)
            endPoint = Stop(UUID.randomUUID().toString(), nameEnd, coordsEnd.first, coordsEnd.second)
            
            for (i in 1 until foundCities.size - 1) {
                val (name, coords) = foundCities[i]
                intermediateStops.add(Stop(UUID.randomUUID().toString(), name, coords.first, coords.second))
            }
        }

        return ParseRouteResult(startPoint, endPoint, intermediateStops, isOffline = true)
    }
}

data class ParseRouteResult(
    val startPoint: Stop,
    val endPoint: Stop,
    val intermediateStops: List<Stop>,
    val isOffline: Boolean,
    val errorMsg: String? = null
)
