package com.surakkha.bd.network

/**
 * Global network configuration and base URLs for backend microservices.
 */
object Constants {

    /**
     * IP address of your development machine on the local Wi-Fi network.
     * - Physical Device: Replace with your PC's current IPv4 address (e.g. 192.168.0.194).
     * - Android Studio Emulator: Replace with "10.0.2.2".
     */
    const val SERVER_IP = "192.168.0.194"

    // Port 3001: Authentication & User Service
    const val AUTH_BASE_URL = "http://$SERVER_IP:3001/"

    // Port 3002: Emergency SOS Incident Service
    const val SOS_BASE_URL = "http://$SERVER_IP:3002/"

    // Port 3003: Geospatial & Proximity Location Service
    const val GEO_SERVICE_BASE_URL = "http://$SERVER_IP:3003/"
    const val GEO_BASE_URL = GEO_SERVICE_BASE_URL
}
