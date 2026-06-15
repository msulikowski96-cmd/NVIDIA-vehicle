package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startName: String,
    val startLat: Double,
    val startLng: Double,
    val endName: String,
    val endLat: Double,
    val endLng: Double,
    val serializedStops: String, // Stringified JSON array of stops
    val serializedOptimizedOrder: String, // Stringified JSON array of Int indices
    val totalDistanceKm: Double,
    val timestamp: Long,
    val solverTypeUsed: String
)
