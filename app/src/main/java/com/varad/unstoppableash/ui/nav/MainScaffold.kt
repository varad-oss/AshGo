package com.varad.unstoppableash.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.components.EmptyState
import com.varad.unstoppableash.ui.theme.Orbit

enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Rounded.Home),
    MAP("map", R.string.tab_map, Icons.Rounded.Map),
    CHAT("chat", R.string.tab_chat, Icons.AutoMirrored.Rounded.Chat),
    SAFETY("safety", R.string.tab_safety, Icons.Rounded.Shield),
}

/** Bottom navigation: Home · Map · Chat · Safety (REDESIGN_SPEC §8), with the unread badge on Chat. */
@Composable
fun MainScaffold(selected: Tab, unread: Int, onSelect: (Tab) -> Unit, tabs: List<Tab> = Tab.entries, content: @Composable (Modifier) -> Unit) {
    val c = Orbit.colors
    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.surface) {
                tabs.forEach { tab ->
                    val unreadText = pluralStringResource(R.plurals.unread_badge_cd, unread, unread)
                    NavigationBarItem(
                        // The item merges and clears its icon's semantics, so the count goes on the item itself.
                        modifier = if (tab == Tab.CHAT && unread > 0) Modifier.semantics { stateDescription = unreadText } else Modifier,
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = {
                            if (tab == Tab.CHAT && unread > 0) {
                                BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.primary, selectedTextColor = c.primary, indicatorColor = c.blush,
                            unselectedIconColor = c.textSecondary, unselectedTextColor = c.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // Consumed so a screen's imePadding() adds only what the keyboard covers beyond the bottom bar.
        content(Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/** Debug-build placeholder for tabs whose stage hasn't been built — never pretends to work. */
@Composable
fun ComingLater(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(null, stringResource(R.string.coming_later_title), stringResource(R.string.coming_later_body))
    }
}
