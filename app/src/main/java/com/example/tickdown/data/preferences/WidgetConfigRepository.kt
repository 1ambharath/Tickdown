package com.example.tickdown.data.preferences

import android.content.Context
import android.content.SharedPreferences

enum class WidgetDisplayMode {
    AUTOMATIC,
    PINNED
}

data class WidgetConfig(
    val appWidgetId: Int,
    val displayMode: WidgetDisplayMode = WidgetDisplayMode.AUTOMATIC,
    val pinnedEventId: Long? = null
)

class WidgetConfigRepository(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences("tickdown_widget_prefs", Context.MODE_PRIVATE)
    )

    fun getWidgetConfig(appWidgetId: Int): WidgetConfig {
        val modeStr = prefs.getString("widget_${appWidgetId}_mode", WidgetDisplayMode.AUTOMATIC.name)
        val mode = try {
            WidgetDisplayMode.valueOf(modeStr ?: WidgetDisplayMode.AUTOMATIC.name)
        } catch (_: Exception) {
            WidgetDisplayMode.AUTOMATIC
        }

        val pinnedId = if (prefs.contains("widget_${appWidgetId}_pinned_id")) {
            val id = prefs.getLong("widget_${appWidgetId}_pinned_id", -1L)
            if (id > 0) id else null
        } else {
            null
        }

        return WidgetConfig(
            appWidgetId = appWidgetId,
            displayMode = mode,
            pinnedEventId = pinnedId
        )
    }

    fun saveWidgetConfig(config: WidgetConfig) {
        prefs.edit().apply {
            putString("widget_${config.appWidgetId}_mode", config.displayMode.name)
            if (config.pinnedEventId != null) {
                putLong("widget_${config.appWidgetId}_pinned_id", config.pinnedEventId)
            } else {
                remove("widget_${config.appWidgetId}_pinned_id")
            }
            apply()
        }
    }

    fun removeWidgetConfig(appWidgetId: Int) {
        prefs.edit().apply {
            remove("widget_${appWidgetId}_mode")
            remove("widget_${appWidgetId}_pinned_id")
            apply()
        }
    }
}
