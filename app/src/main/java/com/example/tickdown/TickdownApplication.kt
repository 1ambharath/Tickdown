package com.example.tickdown

import android.app.Application
import com.example.tickdown.data.local.AppDatabase
import com.example.tickdown.data.preferences.AppPreferencesRepository
import com.example.tickdown.data.preferences.WidgetConfigRepository
import com.example.tickdown.data.repository.CountdownRepositoryImpl
import com.example.tickdown.domain.repository.CountdownRepository
import com.example.tickdown.system.sample.SampleData
import com.example.tickdown.widget.TickdownWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TickdownApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: CountdownRepository by lazy {
        CountdownRepositoryImpl(
            dao = database.countdownDao(),
            context = this,
            onDataChanged = {
                TickdownWidgetUpdater.updateAllWidgetsAsync(this)
            }
        )
    }
    val preferencesRepository: AppPreferencesRepository by lazy { AppPreferencesRepository(this) }
    val widgetConfigRepository: WidgetConfigRepository by lazy { WidgetConfigRepository(this) }

    override fun onCreate() {
        super.onCreate()
        com.example.tickdown.system.scheduler.ReminderReconciliationWorker.schedulePeriodicReconciliation(this)
        com.example.tickdown.widget.WidgetAutoRefreshWorker.schedulePeriodicRefresh(this)
        com.example.tickdown.widget.TickdownWidgetUpdater.schedulePeriodicAlarm(this)
        applicationScope.launch {
            SampleData.seedIfDebug(repository)
        }
        applicationScope.launch {
            database.countdownDao().getAllEventsFlow().collect {
                TickdownWidgetUpdater.updateAllWidgets(this@TickdownApplication)
            }
        }
    }
}
