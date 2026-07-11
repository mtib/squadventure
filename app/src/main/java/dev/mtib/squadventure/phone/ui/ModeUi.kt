package dev.mtib.squadventure.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.model.TransportMode

fun TransportMode.icon(): ImageVector = when (this) {
    TransportMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TransportMode.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    TransportMode.CAR -> Icons.Filled.DirectionsCar
    TransportMode.BOAT -> Icons.Filled.DirectionsBoat
    TransportMode.OTHER -> Icons.Filled.MoreHoriz
}

fun TransportMode.labelRes(): Int = when (this) {
    TransportMode.WALK -> R.string.mode_walk
    TransportMode.BIKE -> R.string.mode_bike
    TransportMode.CAR -> R.string.mode_car
    TransportMode.BOAT -> R.string.mode_boat
    TransportMode.OTHER -> R.string.mode_other
}

/** Single-select [TransportMode] chips (e.g. picking the mode to record in). */
@Composable
fun TransportModeChips(
    selected: TransportMode?,
    onSelect: (TransportMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TransportMode.entries) { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.labelRes())) },
                leadingIcon = {
                    Icon(
                        mode.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/**
 * Multi-select [TransportMode] filter chips. An empty [selected] set means "no filter" (show all);
 * tapping a chip toggles it, so selecting one or more narrows to exactly those modes.
 */
@Composable
fun TransportModeFilterChips(
    selected: Set<TransportMode>,
    onToggle: (TransportMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TransportMode.entries) { mode ->
            FilterChip(
                selected = mode in selected,
                onClick = { onToggle(mode) },
                label = { Text(stringResource(mode.labelRes())) },
                leadingIcon = {
                    Icon(
                        mode.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
