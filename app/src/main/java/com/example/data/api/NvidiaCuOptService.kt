package com.example.data.api

import android.util.Log
import com.example.data.model.Stop
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object NvidiaCuOptService {
    private const val TAG = "NvidiaCuOptService"

    // Default NVCF cuOpt Route Optimization URL endpoint
    const val DEFAULT_ENDPOINT = "https://api.nvcf.nvidia.com/v2/nvcf/pexec/functions/ea489d89-cb4c-47fc-ad02-86981ab250b7"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Solves the VRP using the NVIDIA cuOpt NIM API
     */
    suspend fun solveWithCuOpt(
        apiKey: String,
        endpointUrl: String,
        startPoint: Stop,
        endPoint: Stop,
        stops: List<Stop>
    ): List<Int> = withContext(Dispatchers.IO) {
        val allPoints = mutableListOf<Stop>().apply {
            add(startPoint)
            addAll(stops)
            add(endPoint)
        }
        val n = allPoints.size

        // Generate cost matrix using Haversine distance
        val matrix = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) {
                    matrix[i][j] = 0.0
                } else {
                    matrix[i][j] = calculateHaversineDistance(
                        allPoints[i].latitude, allPoints[i].longitude,
                        allPoints[j].latitude, allPoints[j].longitude
                    )
                }
            }
        }

        // Build Payload
        val payloadStr = buildJsonPayload(matrix, n)
        Log.d(TAG, "Request payload: $payloadStr")

        val mediaType = "application/json".toMediaType()
        val requestBody = payloadStr.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            Log.d(TAG, "Response code: ${response.code}, body: $bodyStr")

            if (!response.isSuccessful) {
                throw Exception("Serwer cuOpt zwrócił kod błędu: ${response.code}\n$bodyStr")
            }

            // Parse response to find route indices
            val optimizedRoute = parseRouteFromResponse(bodyStr, n)
            optimizedRoute
        } catch (e: Exception) {
            Log.e(TAG, "Błąd cuOpt solver", e)
            throw e
        }
    }

    /**
     * Haversine distance calculation between two coordinates (km)
     */
    fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Promień Ziemi w km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * Dynamically builds the standard cuOpt JSON requesting body
     */
    fun buildJsonPayload(matrix: Array<DoubleArray>, size: Int): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"data\": {\n")
        sb.append("    \"cost_matrix_data\": {\n")
        sb.append("      \"matrix_0\": [\n")
        for (i in 0 until size) {
            sb.append("        [")
            for (j in 0 until size) {
                sb.append(String.format(java.util.Locale.US, "%.4f", matrix[i][j]))
                if (j < size - 1) sb.append(", ")
            }
            sb.append("]")
            if (i < size - 1) sb.append(",\n") else sb.append("\n")
        }
        sb.append("      ]\n")
        sb.append("    },\n")

        // FleetData: Vehicle starts at 0, ends at last node
        sb.append("    \"fleet_data\": {\n")
        sb.append("      \"vehicle_locations\": [[0, ").append(size - 1).append("]],\n")
        sb.append("      \"vehicle_ids\": [\"sprinter_0\"]\n")
        sb.append("    },\n")

        // TaskData: Stops to visit (nodes 1 to size - 2)
        sb.append("    \"task_data\": {\n")
        sb.append("      \"task_locations\": [")
        for (i in 1..(size - 2)) {
            sb.append(i)
            if (i < size - 2) sb.append(", ")
        }
        sb.append("],\n")
        sb.append("      \"task_ids\": [")
        for (i in 1..(size - 2)) {
            sb.append("\"stop_").append(i).append("\"")
            if (i < size - 2) sb.append(", ")
        }
        sb.append("]\n")
        sb.append("    }\n")
        sb.append("  }\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Parses the response from cuOpt NIM
     */
    private fun parseRouteFromResponse(responseBody: String, size: Int): List<Int> {
        try {
            // Find route list using basic regex or string parsing for maximum bulletproof-ness
            // Schema contains: routes: { sprinter_0: { route: [0, 2, 1, 3] } } or similar
            // Let's search for "route": [ ... ] or "delivery_locations"
            val routePattern = "\"route\"\\s*:\\s*\\[([^\\]]+)\\]".toRegex()
            val match = routePattern.find(responseBody)
            if (match != null) {
                val indicesStr = match.groupValues[1]
                val indices = indicesStr.split(",").map { it.trim().toInt() }
                return indices
            }

            val locationsPattern = "\"delivery_locations\"\\s*:\\s*\\[([^\\]]+)\\]".toRegex()
            val matchLoc = locationsPattern.find(responseBody)
            if (matchLoc != null) {
                val indicesStr = matchLoc.groupValues[1]
                val elements = indicesStr.split(",").map { it.trim().toInt() }
                // Construct path: starts with 0, then task delivery locations, then ends with size - 1
                return listOf(0) + elements + listOf(size - 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsowanie JSON nvidia cuopt nie powiodło się, błąd: ${e.message}")
        }

        // Fallback: Default path sequence
        return (0 until size).toList()
    }
}
