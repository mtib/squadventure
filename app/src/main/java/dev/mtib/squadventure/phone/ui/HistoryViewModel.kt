package dev.mtib.squadventure.phone.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.model.ActivityMeta
import dev.mtib.squadventure.phone.GpxImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ActivityRepository(app)

    private val _activities = MutableStateFlow<List<ActivityMeta>>(emptyList())
    val activities: StateFlow<List<ActivityMeta>> = _activities.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _activities.value = repo.list()
            _loading.value = false
        }
    }

    suspend fun importUris(uris: List<Uri>): String = withContext(Dispatchers.IO) {
        val summary = GpxImporter.importUris(getApplication(), repo, uris)
        _activities.value = repo.list()
        GpxImporter.message(getApplication(), summary)
    }
}
