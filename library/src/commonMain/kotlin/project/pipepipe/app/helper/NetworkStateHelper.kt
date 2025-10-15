package project.pipepipe.app.helper

import dev.tmapps.konnection.Konnection
import dev.tmapps.konnection.NetworkConnection

object NetworkStateHelper {
    private val konnection = Konnection.instance

    /**
     * Check if currently connected via WiFi
     * @return true if connected via WiFi, false otherwise
     */
    fun isWifiConnected(): Boolean {
        return konnection.getCurrentNetworkConnection() == NetworkConnection.WIFI
    }

    /**
     * Check if currently connected to internet
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean {
        return konnection.isConnected()
    }

}
