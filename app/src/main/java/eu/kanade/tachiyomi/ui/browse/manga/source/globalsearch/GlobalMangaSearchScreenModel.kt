package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import androidx.compose.runtime.Immutable
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalMangaSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String = "",
    preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
) : MangaSearchScreenModel<GlobalMangaSearchScreenModel.State>(
    State(
        searchQuery = initialQuery,
    ),
) {

    val incognitoMode = preferences.incognitoMode()
    val lastUsedSourceId = sourcePreferences.lastUsedMangaSource()

    val searchPagerFlow = state.map { Pair(it.onlyShowHasResults, it.items) }
        .distinctUntilChanged()
        .map { (onlyShowHasResults, items) ->
            items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, state.value.items)

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || initialExtensionFilter.isNotBlank()) {
            search(initialQuery)
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledMangaSources().get()
        val pinnedSources = sourcePreferences.pinnedMangaSources().get()

        return sourceManager.getCatalogueSources()
            .filter { mutableState.value.sourceFilter != MangaSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .filter { it.lang in enabledLanguages }
            .filterNot { "${it.id}" in disabledSources }
            .sortedWith(compareBy({ "${it.id}" !in pinnedSources }, { "${it.name.lowercase()} (${it.lang})" }))
    }

    override fun updateSearchQuery(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    override fun updateItems(items: Map<CatalogueSource, MangaSearchItemResult>) {
        mutableState.update {
            it.copy(items = items)
        }
    }

    override fun getItems(): Map<CatalogueSource, MangaSearchItemResult> {
        return mutableState.value.items
    }

    fun setSourceFilter(filter: MangaSourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
    }

    fun toggleFilterResults() {
        mutableState.update {
            it.copy(onlyShowHasResults = !it.onlyShowHasResults)
        }
    }

    private fun MangaSearchItemResult.isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is MangaSearchItemResult.Success && !this.isEmpty)
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val sourceFilter: MangaSourceFilter = MangaSourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: Map<CatalogueSource, MangaSearchItemResult> = emptyMap(),
    ) {
        val progress: Int = items.count { it.value !is MangaSearchItemResult.Loading }
        val total: Int = items.size
    }
}
