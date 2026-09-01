package com.lime.touchlab.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lime.rawtouchcollector.RawTouchCollector
import com.lime.touchlab.export.SessionExporter
import com.lime.touchlab.storage.TouchLabDatabase
import com.lime.touchlab.storage.TrialRepository

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TouchLabDatabase.get(application)
    private val repository = TrialRepository(database)

    val exporter: SessionExporter = SessionExporter(application, database.dao())

    val controller: SessionController = SessionController(
        collector = RawTouchCollector(application),
        repository = repository,
        exporter = exporter,
    )

    override fun onCleared() {
        // shutdown() присоединяется к worker-потоку, поэтому уходит на свой поток:
        // onCleared приходит на главном, а блокировать его нельзя.
        controller.bindDisplaySource(null)
        controller.shutdown()
        super.onCleared()
    }
}
