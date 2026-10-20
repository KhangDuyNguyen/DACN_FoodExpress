package vn.hutech.foodexpress.core.network

import vn.hutech.foodexpress.core.BuildConfig

object ApiConfig {
    const val LOCAL_EMULATOR_BASE_URL = "http://10.0.2.2:8080/api/"
    private const val UNCONFIGURED_RELEASE_BASE_URL = "https://example.invalid/api/"

    val baseUrl: String
        get() = if (BuildConfig.DEBUG) {
            LOCAL_EMULATOR_BASE_URL
        } else {
            UNCONFIGURED_RELEASE_BASE_URL
        }
}
