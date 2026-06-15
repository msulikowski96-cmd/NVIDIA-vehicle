package com.example.ui.optimizer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiGeocodingService
import com.example.data.api.LocalRouteSolver
import com.example.data.api.NvidiaCuOptService
import com.example.data.database.RouteDatabase
import com.example.data.database.RouteEntity
import com.example.data.model.Stop
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class RouteOptimizerViewModel(application: Application) : AndroidViewModel(application) {

    private val routeDao = RouteDatabase.getInstance(application).routeDao

    // State flows for UI
    val startPoint = MutableStateFlow<Stop?>(
        Stop(UUID.randomUUID().toString(), "Warszawa (PKiN)", 52.2318, 21.0060)
    )
    val endPoint = MutableStateFlow<Stop?>(
        Stop(UUID.randomUUID().toString(), "Kraków (Sukiennice)", 50.0617, 19.9373)
    )
    val stops = MutableStateFlow<List<Stop>>(
        listOf(
            Stop(UUID.randomUUID().toString(), "Katowice (Spodek)", 50.2662, 19.0253),
            Stop(UUID.randomUUID().toString(), "Wrocław (Rynek)", 51.1097, 17.0322),
            Stop(UUID.randomUUID().toString(), "Częstochowa (Jasna Góra)", 50.8126, 19.0974)
        )
    )

    val optimizedRoute = MutableStateFlow<List<Stop>>(emptyList())
    val totalDistanceKm = MutableStateFlow(0.0)
    val isOptimizing = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val solverUsed = MutableStateFlow("Local Solver")

    // NVIDIA Optimization Configurations
    val nvidiaApiKey = MutableStateFlow("")
    val nvidiaEndpointUrl = MutableStateFlow(NvidiaCuOptService.DEFAULT_ENDPOINT)
    val isNvidiaEnabled = MutableStateFlow(false)

    // Raw payload inspectors for cuOpt learning/inspection
    val jsonRequestPayload = MutableStateFlow("")
    val jsonResponsePayload = MutableStateFlow("")

    // Room DB History
    val routeHistory: StateFlow<List<RouteEntity>> = routeDao.getAllRoutes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val stopsListAdapter = moshi.adapter<List<Stop>>(
        Types.newParameterizedType(List::class.java, Stop::class.java)
    )
    private val indicesAdapter = moshi.adapter<List<Int>>(
        Types.newParameterizedType(List::class.java, java.lang.Integer::class.java)
    )

    init {
        // Run initial optimization calculation so screen loads with optimized path
        triggerLocalOptimization()
    }

    // Setters
    fun setStartPoint(name: String, latitude: Double, longitude: Double) {
        startPoint.value = Stop(UUID.randomUUID().toString(), name, latitude, longitude)
        optimizedRoute.value = emptyList() // Request re-optimization
    }

    fun setEndPoint(name: String, latitude: Double, longitude: Double) {
        endPoint.value = Stop(UUID.randomUUID().toString(), name, latitude, longitude)
        optimizedRoute.value = emptyList() // Request re-optimization
    }

    fun addStop(name: String, latitude: Double, longitude: Double) {
        val currentStops = stops.value.toMutableList()
        currentStops.add(Stop(UUID.randomUUID().toString(), name, latitude, longitude))
        stops.value = currentStops
        optimizedRoute.value = emptyList() // Request re-optimization
    }

    fun removeStop(id: String) {
        val currentStops = stops.value.filterNot { it.id == id }
        stops.value = currentStops
        optimizedRoute.value = emptyList() // Request re-optimization
    }

    fun updateStop(id: String, name: String, latitude: Double, longitude: Double) {
        val currentStops = stops.value.map {
            if (it.id == id) Stop(id, name, latitude, longitude) else it
        }
        stops.value = currentStops
        optimizedRoute.value = emptyList() // Request re-optimization
    }

    /**
     * Executes Route Optimization via local 2-opt or NVIDIA cuOpt NIM API
     */
    fun optimizeRoute() {
        val start = startPoint.value ?: return
        val end = endPoint.value ?: return
        val rawStops = stops.value

        viewModelScope.launch {
            isOptimizing.value = true
            errorMessage.value = null

            // Generate full path elements to create the distance/cost matrix
            val allPoints = mutableListOf<Stop>().apply {
                add(start)
                addAll(rawStops)
                add(end)
            }
            val size = allPoints.size

            // Pre-calculate standard distance matrix for inspection
            val matrix = Array(size) { DoubleArray(size) }
            for (i in 0 until size) {
                for (j in 0 until size) {
                    matrix[i][j] = if (i == j) 0.0 else NvidiaCuOptService.calculateHaversineDistance(
                        allPoints[i].latitude, allPoints[i].longitude,
                        allPoints[j].latitude, allPoints[j].longitude
                    )
                }
            }

            // Export cuOpt Request Payload to Inspector State Flow
            jsonRequestPayload.value = NvidiaCuOptService.buildJsonPayload(matrix, size)

            if (isNvidiaEnabled.value && nvidiaApiKey.value.isNotBlank()) {
                // Call NVIDIA cuOpt REST Microservice
                try {
                    solverUsed.value = "NVIDIA cuOpt (NIM)"
                    val optimizedIndices = NvidiaCuOptService.solveWithCuOpt(
                        apiKey = nvidiaApiKey.value,
                        endpointUrl = nvidiaEndpointUrl.value,
                        startPoint = start,
                        endPoint = end,
                        stops = rawStops
                    )

                    // Display mock structured successful response in inspection tab
                    jsonResponsePayload.value = buildMockCuOptResponseJson(optimizedIndices, size)

                    // Re-order Stops list
                    reorderStops(optimizedIndices, allPoints)
                    saveCurrentRouteToDb()

                } catch (e: Exception) {
                    Log.e("ViewModel", "cuOpt API call failed, falling back to Local Optimizer", e)
                    errorMessage.value = "cuOpt error: ${e.localizedMessage}. Uruchomiono lokalny optymalizator jako fallback."
                    // Fallback to local
                    solverUsed.value = "Local Fallback (cuOpt Kompatybilny)"
                    triggerLocalOptimization()
                    saveCurrentRouteToDb()
                }
            } else {
                // Run Client-Side Optimizer
                solverUsed.value = "Lokalny Silnik (2-Opt/BruteForce)"
                triggerLocalOptimization()
                saveCurrentRouteToDb()
            }

            isOptimizing.value = false
        }
    }

    private fun triggerLocalOptimization() {
        val start = startPoint.value ?: return
        val end = endPoint.value ?: return
        val rawStops = stops.value
        val allPoints = mutableListOf<Stop>().apply {
            add(start)
            addAll(rawStops)
            add(end)
        }

        val optimizedIndices = LocalRouteSolver.solveTspLocally(start, end, rawStops)
        // Set Mock simulation JSON response for cuOpt inspection
        jsonResponsePayload.value = buildMockCuOptResponseJson(optimizedIndices, allPoints.size)
        reorderStops(optimizedIndices, allPoints)
    }

    private fun reorderStops(indices: List<Int>, allPoints: List<Stop>) {
        val finalOrderedList = mutableListOf<Stop>()
        var distanceSum = 0.0

        for (k in 0 until indices.size) {
            val idx = indices[k]
            if (idx in allPoints.indices) {
                finalOrderedList.add(allPoints[idx])
                if (k > 0) {
                    val prevIdx = indices[k - 1]
                    distanceSum += NvidiaCuOptService.calculateHaversineDistance(
                        allPoints[prevIdx].latitude, allPoints[prevIdx].longitude,
                        allPoints[idx].latitude, allPoints[idx].longitude
                    )
                }
            }
        }
        optimizedRoute.value = finalOrderedList
        totalDistanceKm.value = distanceSum
    }

    /**
     * Resolves user prompt using Gemini Geocoding service
     */
    fun parseRouteWithAI(prompt: String) {
        if (prompt.isBlank()) return

        viewModelScope.launch {
            isOptimizing.value = true
            errorMessage.value = null
            try {
                val result = GeminiGeocodingService.parseNaturalLanguageRoute(prompt)
                
                // Set locations from AI geocoding response
                startPoint.value = result.startPoint
                endPoint.value = result.endPoint
                stops.value = result.intermediateStops
                
                if (result.isOffline) {
                    errorMessage.value = result.errorMsg ?: "Informacja: Sparsowano trasy lokalnym silnikiem offline."
                }

                // Run optimization automatically
                optimizeRoute()
            } catch (e: Exception) {
                errorMessage.value = "Błąd parsowania AI: ${e.localizedMessage}"
            } finally {
                isOptimizing.value = false
            }
        }
    }

    /**
     * Stash route in Room database
     */
    private suspend fun saveCurrentRouteToDb() {
        val start = startPoint.value ?: return
        val end = endPoint.value ?: return
        val rawStops = stops.value
        val orderedList = optimizedRoute.value
        val orderedIndices = orderedList.map { stop ->
            // Match stop coordinates back to original index sequence
            val orig = mutableListOf<Stop>().apply {
                add(start)
                addAll(rawStops)
                add(end)
            }
            orig.indexOfFirst { it.latitude == stop.latitude && it.longitude == stop.longitude }
        }

        try {
            val stopsJsonStr = stopsListAdapter.toJson(rawStops)
            val indicesJsonStr = indicesAdapter.toJson(orderedIndices)

            val routeEntity = RouteEntity(
                title = "Trasa: ${start.name} ➡️ ${end.name} (${rawStops.size} przyst.)",
                startName = start.name,
                startLat = start.latitude,
                startLng = start.longitude,
                endName = end.name,
                endLat = end.latitude,
                endLng = end.longitude,
                serializedStops = stopsJsonStr,
                serializedOptimizedOrder = indicesJsonStr,
                totalDistanceKm = totalDistanceKm.value,
                timestamp = System.currentTimeMillis(),
                solverTypeUsed = solverUsed.value
            )

            routeDao.insertRoute(routeEntity)
        } catch (e: Exception) {
            Log.e("ViewModel", "Błąd zapisu historii trasy", e)
        }
    }

    /**
     * Reload routing states from historical DB record
     */
    fun loadRouteFromHistory(entity: RouteEntity) {
        try {
            val loadedStops = stopsListAdapter.fromJson(entity.serializedStops) ?: emptyList()
            val loadedIndices = indicesAdapter.fromJson(entity.serializedOptimizedOrder) ?: emptyList()

            startPoint.value = Stop(UUID.randomUUID().toString(), entity.startName, entity.startLat, entity.startLng)
            endPoint.value = Stop(UUID.randomUUID().toString(), entity.endName, entity.endLat, entity.endLng)
            stops.value = loadedStops

            val allPoints = mutableListOf<Stop>().apply {
                add(startPoint.value!!)
                addAll(loadedStops)
                add(endPoint.value!!)
            }

            reorderStops(loadedIndices, allPoints)
            solverUsed.value = entity.solverTypeUsed
            totalDistanceKm.value = entity.totalDistanceKm
        } catch (e: Exception) {
            errorMessage.value = "Nie udało się załadować trasy: ${e.localizedMessage}"
        }
    }

    fun deleteRouteHistory(id: Int) {
        viewModelScope.launch {
            routeDao.deleteRouteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            routeDao.clearHistory()
        }
    }

    /**
     * Builds mock success JSON for cuOpt NIM visualizer panel
     */
    private fun buildMockCuOptResponseJson(indices: List<Int>, size: Int): String {
        return "{\n" +
                "  \"response\": {\n" +
                "    \"solver_response\": {\n" +
                "      \"status\": \"SUCCESS\",\n" +
                "      \"solver_time_ms\": 1.482,\n" +
                "      \"routes\": {\n" +
                "        \"sprinter_0\": {\n" +
                "          \"task_ids\": " + indices.filter { !listOf(0, size - 1).contains(it) }.map { "\"stop_$it\"" } + ",\n" +
                "          \"delivery_locations\": " + indices.filter { !listOf(0, size - 1).contains(it) } + ",\n" +
                "          \"route\": $indices\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}"
    }
}
