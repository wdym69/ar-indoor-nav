package com.example.indoornavdisha

object WaypointStorage {
    /** List of waypoints fetched from backend */
    var waypoints: List<Waypoint> = emptyList()

    /** 4×4 transformation matrix fetched from backend */
    var transformationMatrix: List<List<Double>> = emptyList()
}
