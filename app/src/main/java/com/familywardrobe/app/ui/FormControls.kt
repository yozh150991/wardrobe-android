@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.familywardrobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** UI-05: a short single choice as chips on screen (season, style), instead of a menu that hides the options. */
@Composable
fun ChipChoice(value: String, options: List<String>, change: (String) -> Unit, title: Int, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() })
        FlowRow(Modifier.semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { code ->
                PillChip(code == value, stringResource(label(code)), { change(code) }, enabled = enabled)
            }
        }
    }
}

/** UI-03 / LOOK-02: a filter chip of the new design — white on the background, the chosen one dark.
 *  LOOK-07: every choice chip in the app is this one (or ChipChoice), so no screen keeps the old outlined chip. */
@Composable
internal fun PillChip(selected: Boolean, title: String, click: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null, singleLine: Boolean = false) {
    FilterChip(selected, onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50), border = null, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        label = { Text(title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE, softWrap = !singleLine) },
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface))
}

/** UI-03 / LOOK-02: the search field of the new design — a white rounded field with a search icon and a clear button. */
@Composable
internal fun SearchBox(query: String, change: (String) -> Unit, hint: String, clear: String, modifier: Modifier = Modifier) {
    TextField(query, change, modifier = modifier, singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp),
        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { change("") }) { Icon(Icons.Outlined.Close, clear) } }) else null,
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent))
}
