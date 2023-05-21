package com.github.libretube.api.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Instances(
    val name: String,
    @SerialName("api_url") val apiUrl: String,
    val locations: String = "",
    val version: String = "",
    @SerialName("up_to_date") val upToDate: Boolean = true,
    val cdn: Boolean = false,
    val registered: Long = 0,
    @SerialName("last_checked") val lastChecked: Long = 0,
)
