package com.climaai.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Pull-to-refresh container matching the Material3 1.3 `PullToRefreshBox` API,
 * implemented on top of the 1.2 primitives this project targets.
 *
 * Material3 1.3 requires a newer Kotlin than the project is on, so rather than
 * drop the gesture or force a toolchain upgrade, this keeps the call site
 * identical to the modern API. Delete it and import the real thing once the
 * project moves to Material3 1.3+.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    // The 1.2 container drives refreshing from the gesture, so the callback
    // fires here rather than being passed in.
    if (state.isRefreshing) {
        LaunchedEffect(Unit) { currentOnRefresh() }
    }

    // Let the caller's state end the indicator when the refresh completes.
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && state.isRefreshing) {
            state.endRefresh()
        }
    }

    Box(modifier = modifier.nestedScroll(state.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
