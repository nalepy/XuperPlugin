package com.xuper.plugin

object PluginContract {
    const val ACTION_PLUGIN_SERVICE = "com.streamvault.plugin.API"

    const val MSG_GET_MANIFEST = 1
    const val MSG_SET_ENABLED = 2
    const val MSG_GET_STATUS = 3
    const val MSG_GET_PROVIDER_URL = 4
    const val MSG_PREPARE_PLAYBACK = 5
    const val MSG_REWRITE_CAST_URL = 6
    const val MSG_GET_CONFIGURATION_SCHEMA = 7
    const val MSG_GET_CONFIGURATION_VALUES = 8
    const val MSG_SET_CONFIGURATION_VALUES = 9
    const val MSG_RUN_CONFIGURATION_ACTION = 10

    const val KEY_API_VERSION = "api_version"
    const val KEY_REQUEST_ID = "request_id"
    const val KEY_SUCCESS = "success"
    const val KEY_MESSAGE = "message"
    const val KEY_ENABLED = "enabled"
    const val KEY_STATUS_LABEL = "status_label"
    const val KEY_URL = "url"
    const val KEY_PROVIDER_NAME = "provider_name"
    const val KEY_INPUT_URL = "input_url"
    const val KEY_OUTPUT_URL = "output_url"
    const val KEY_HANDLED = "handled"
    const val KEY_STREAM_TYPE = "stream_type"
    const val KEY_MANIFEST_JSON = "manifest_json"
    const val KEY_CONFIGURATION_SCHEMA_JSON = "configuration_schema_json"
    const val KEY_CONFIGURATION_VALUES_JSON = "configuration_values_json"
    const val KEY_CONFIGURATION_ACTION_ID = "configuration_action_id"

    const val API_VERSION = 1

    val MANIFEST_JSON = """
        {
            "schemaVersion": 1,
            "id": "com.xuper.plugin",
            "name": "Xuper BrazilTV",
            "versionName": "1.0.0",
            "versionCode": 1,
            "description": "Xuper/BrazilTV IPTV provider plugin",
            "providerName": "Xuper BrazilTV",
            "configurationMode": "activity",
            "configurationActivityAction": "com.xuper.plugin.CONFIGURE",
            "capabilities": ["provider.m3u", "playback.prepare", "configuration.activity"]
        }
    """.trimIndent()
}
