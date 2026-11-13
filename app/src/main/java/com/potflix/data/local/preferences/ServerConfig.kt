package com.potflix.data.local.preferences

enum class ServerType { FTP, ALIST }

data class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val type: ServerType,
    val isBuiltIn: Boolean
) {
    companion object {
        val DHAKAFLIX = ServerConfig(
            id = "dhakaflix",
            name = "Dhakaflix (Local)",
            baseUrl = "http://172.16.50.7/DHAKA-FLIX-7/", // Reachability check; catalogue URLs carry their own host
            type = ServerType.FTP,
            isBuiltIn = true
        )

        val NAGORDOLA = ServerConfig(
            id = "nagordola",
            name = "Nagordola CDN",
            baseUrl = "https://cdn.nagordola.com.bd/",
            type = ServerType.ALIST,
            isBuiltIn = true
        )

        val BUILT_IN_SERVERS = listOf(DHAKAFLIX)
    }
}
