package com.example.data.api

import com.example.data.model.Stop

object LocalRouteSolver {

    /**
     * Solves VRP/TSP locally. Returns list of point indices in optimized order.
     * Index 0 is starting point, Index N-1 is ending point, and intermediate indices
     * are optimized to minimize total great-circle distance.
     */
    fun solveTspLocally(
        startPoint: Stop,
        endPoint: Stop,
        stops: List<Stop>
    ): List<Int> {
        val allPoints = mutableListOf<Stop>().apply {
            add(startPoint)
            addAll(stops)
            add(endPoint)
        }
        val n = allPoints.size

        if (n <= 2) {
            return (0 until n).toList()
        }

        // Generate matrix of distances
        val matrix = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) {
                    matrix[i][j] = 0.0
                } else {
                    matrix[i][j] = NvidiaCuOptService.calculateHaversineDistance(
                        allPoints[i].latitude, allPoints[i].longitude,
                        allPoints[j].latitude, allPoints[j].longitude
                    )
                }
            }
        }

        // If intermediate stops count is <= 8, solve exactly using brute-force search over all permutations.
        val intermediateCount = stops.size
        return if (intermediateCount <= 8) {
            solveExactTsp(matrix, n)
        } else {
            solveApproximateTsp(matrix, n)
        }
    }

    /**
     * Exact TSP solver using permutation search for and <= 8 stops.
     */
    private fun solveExactTsp(matrix: Array<DoubleArray>, n: Int): List<Int> {
        val intermediateIndices = (1..(n - 2)).toList()
        var bestRoute = (0 until n).toList()
        var minDistance = Double.MAX_VALUE

        // Generate all permutations of intermediate stops
        fun permute(list: List<Int>, l: Int, r: Int) {
            if (l == r) {
                // Calculate distance of this path: 0 -> permuted stops -> n-1
                var distance = matrix[0][list[0]]
                for (i in 0 until list.size - 1) {
                    distance += matrix[list[i]][list[i + 1]]
                }
                distance += matrix[list.last()][n - 1]

                if (distance < minDistance) {
                    minDistance = distance
                    bestRoute = listOf(0) + list + listOf(n - 1)
                }
            } else {
                val mutableList = list.toMutableList()
                for (i in l..r) {
                    swap(mutableList, l, i)
                    permute(mutableList, l + 1, r)
                    swap(mutableList, l, i)
                }
            }
        }

        if (intermediateIndices.isNotEmpty()) {
            permute(intermediateIndices, 0, intermediateIndices.size - 1)
        }
        return bestRoute
    }

    private fun swap(list: MutableList<Int>, i: Int, j: Int) {
        val temp = list[i]
        list[i] = list[j]
        list[j] = temp
    }

    /**
     * Nearest Neighbor heuristic followed by 2-opt refinement for larger size inputs.
     */
    private fun solveApproximateTsp(matrix: Array<DoubleArray>, n: Int): List<Int> {
        val unvisited = (1..(n - 2)).toMutableSet()
        val route = mutableListOf<Int>().apply { add(0) }

        // Step 1: Nearest Neighbor Construction
        var current = 0
        while (unvisited.isNotEmpty()) {
            val next = unvisited.minByOrNull { matrix[current][it] } ?: break
            route.add(next)
            unvisited.remove(next)
            current = next
        }
        route.add(n - 1) // End point is fixed

        // Step 2: 2-Opt refinement for the intermediate parts
        // A route is [0, a, b, c, ..., n-1]. We only reverse segments between index 1 and n-2.
        var improved = true
        var loopCount = 0
        while (improved && loopCount < 100) {
            improved = false
            for (i in 1 until n - 2) {
                for (j in i + 1 until n - 1) {
                    // Cost change if we reverse segment from i to j
                    // Old cost: dist(i-1, i) + dist(j, j+1)
                    // New cost: dist(i-1, j) + dist(i, j+1)
                    val oldCost = matrix[route[i - 1]][route[i]] + matrix[route[j]][route[j + 1]]
                    val newCost = matrix[route[i - 1]][route[j]] + matrix[route[i]][route[j + 1]]
                    
                    if (newCost < oldCost - 1e-5) {
                        // Reverse the sublist from i to j
                        route.subList(i, j + 1).reverse()
                        improved = true
                    }
                }
            }
            loopCount++
        }

        return route
    }
}
