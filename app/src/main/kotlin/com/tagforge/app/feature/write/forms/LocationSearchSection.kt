package com.tagforge.app.feature.write.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.android.PlaceResult
import com.tagforge.app.infrastructure.android.PlaceSearch
import com.tagforge.app.infrastructure.android.PlaceSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What the address search is doing. Kept apart from the record so searching never changes it until a match is tapped. */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val places: List<PlaceResult>) : SearchState
    data class Message(val textRes: Int) : SearchState
}

/** "Search an address or place": type, press Search, tap the match. Needs no permission. */
@Composable
fun LocationSearchSection(onPick: (PlaceResult) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val search = remember { PlaceSearch(context.applicationContext, Dispatchers.IO) }
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    fun startSearch() {
        if (query.isBlank() || state == SearchState.Loading) return
        state = SearchState.Loading
        scope.launch {
            state = when (val result = search.search(query)) {
                is PlaceSearchResult.Found -> SearchState.Results(result.places)
                PlaceSearchResult.NoMatches -> SearchState.Message(R.string.loc_search_none)
                PlaceSearchResult.NoBackend -> SearchState.Message(R.string.loc_search_no_backend)
                PlaceSearchResult.Failed -> SearchState.Message(R.string.loc_search_failed)
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.loc_search_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.loc_search_label)) },
            placeholder = { Text(stringResource(R.string.loc_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { startSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = ::startSearch, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.loc_search_button))
        }
        when (val current = state) {
            SearchState.Idle -> Unit
            SearchState.Loading -> CircularProgressIndicator()
            is SearchState.Message -> Text(stringResource(current.textRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is SearchState.Results -> current.places.forEach { place -> PlaceRow(place) { onPick(place) } }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceResult, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(place.label, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${place.latitude}, ${place.longitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
