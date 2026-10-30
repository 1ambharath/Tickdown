package com.example.tickdown.widget

import android.content.SharedPreferences
import com.example.tickdown.data.preferences.WidgetConfig
import com.example.tickdown.data.preferences.WidgetConfigRepository
import com.example.tickdown.data.preferences.WidgetDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WidgetConfigRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: WidgetConfigRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = WidgetConfigRepository(fakePrefs)
    }

    @Test
    fun getWidgetConfig_defaultIsAutomatic() {
        val config = repository.getWidgetConfig(appWidgetId = 101)

        assertEquals(101, config.appWidgetId)
        assertEquals(WidgetDisplayMode.AUTOMATIC, config.displayMode)
        assertNull(config.pinnedEventId)
    }

    @Test
    fun saveWidgetConfig_pinnedMode_storesAndRetrievesCorrectly() {
        val config = WidgetConfig(
            appWidgetId = 202,
            displayMode = WidgetDisplayMode.PINNED,
            pinnedEventId = 42L
        )

        repository.saveWidgetConfig(config)
        val retrieved = repository.getWidgetConfig(appWidgetId = 202)

        assertEquals(202, retrieved.appWidgetId)
        assertEquals(WidgetDisplayMode.PINNED, retrieved.displayMode)
        assertEquals(42L, retrieved.pinnedEventId)
    }

    @Test
    fun removeWidgetConfig_clearsConfig() {
        val config = WidgetConfig(
            appWidgetId = 303,
            displayMode = WidgetDisplayMode.PINNED,
            pinnedEventId = 99L
        )

        repository.saveWidgetConfig(config)
        repository.removeWidgetConfig(303)

        val retrieved = repository.getWidgetConfig(303)
        assertEquals(WidgetDisplayMode.AUTOMATIC, retrieved.displayMode)
        assertNull(retrieved.pinnedEventId)
    }

    @Test
    fun multipleWidgetInstances_remainCompletelyIndependent() {
        // Instance 1: Configured for Automatic Nearest Sorting
        val configInstance1 = WidgetConfig(
            appWidgetId = 1001,
            displayMode = WidgetDisplayMode.AUTOMATIC,
            pinnedEventId = null
        )
        // Instance 2: Pinned to Event 42
        val configInstance2 = WidgetConfig(
            appWidgetId = 1002,
            displayMode = WidgetDisplayMode.PINNED,
            pinnedEventId = 42L
        )
        // Instance 3: Pinned to Event 88
        val configInstance3 = WidgetConfig(
            appWidgetId = 1003,
            displayMode = WidgetDisplayMode.PINNED,
            pinnedEventId = 88L
        )

        repository.saveWidgetConfig(configInstance1)
        repository.saveWidgetConfig(configInstance2)
        repository.saveWidgetConfig(configInstance3)

        // Verify each instance preserves its distinct configuration
        assertEquals(WidgetDisplayMode.AUTOMATIC, repository.getWidgetConfig(1001).displayMode)
        assertNull(repository.getWidgetConfig(1001).pinnedEventId)

        assertEquals(WidgetDisplayMode.PINNED, repository.getWidgetConfig(1002).displayMode)
        assertEquals(42L, repository.getWidgetConfig(1002).pinnedEventId)

        assertEquals(WidgetDisplayMode.PINNED, repository.getWidgetConfig(1003).displayMode)
        assertEquals(88L, repository.getWidgetConfig(1003).pinnedEventId)

        // Deleting instance 2 does not alter instance 1 or instance 3
        repository.removeWidgetConfig(1002)
        assertEquals(WidgetDisplayMode.AUTOMATIC, repository.getWidgetConfig(1001).displayMode)
        assertEquals(WidgetDisplayMode.PINNED, repository.getWidgetConfig(1003).displayMode)
        assertEquals(88L, repository.getWidgetConfig(1003).pinnedEventId)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val storage: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) { if (value != null) pending[key] = value else pending[key] = null }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) storage.clear()
                pending.forEach { (k, v) ->
                    if (v == null) storage.remove(k) else storage[k] = v
                }
                pending.clear()
            }
        }
    }
}
