package com.example.bazadanych.data.calculation

import org.osmdroid.util.GeoPoint

object GeoUtils {
    fun calculateAreaInHectares(points: List<GeoPoint>): Double {
        if (points.size < 3) return 0.0

        var totalArea = 0.0
        val r = 6378137.0 // Promień Ziemi w metrach

        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]

            val lat1 = Math.toRadians(p1.latitude)
            val lon1 = Math.toRadians(p1.longitude)
            val lat2 = Math.toRadians(p2.latitude)
            val lon2 = Math.toRadians(p2.longitude)

            totalArea += (lon2 - lon1) * (2 + Math.sin(lat1) + Math.sin(lat2))
        }

        val areaInSqMeters = Math.abs(totalArea * r * r / 2.0)
        return areaInSqMeters / 10000.0 // Przelicz na hektary
    }
}