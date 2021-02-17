package com.vsulimov.android

import com.vsulimov.android.lifecycle.ActivityLifecycleAction
import com.vsulimov.redux.AbstractStore
import com.vsulimov.redux.Action
import com.vsulimov.redux.Middleware
import com.vsulimov.redux.Reducer

/**
 * Redux application state.
 */
data class ApplicationState(
    val onCreateActionsCount: Int = 0,
    val onStartActionsCount: Int = 0,
    val onResumeActionsCount: Int = 0,
    val onPauseActionsCount: Int = 0,
    val onStopActionsCount: Int = 0,
    val onDestroyActionsCount: Int = 0
)

/**
 * Redux application store.
 */
class ApplicationStore(
    initialState: ApplicationState = ApplicationState(),
    middlewares: List<Middleware<ApplicationState>>,
    reducers: List<Reducer<ApplicationState>>
) : AbstractStore<ApplicationState>(initialState, middlewares, reducers)

object ApplicationReducer : Reducer<ApplicationState> {
    override fun reduce(action: Action, state: ApplicationState): ApplicationState {
        return when (action) {
            is ActivityLifecycleAction ->
                reduceActivityLifecycle(action, state)
            else ->
                state
        }
    }
}

private fun reduceActivityLifecycle(
    action: ActivityLifecycleAction,
    state: ApplicationState
): ApplicationState {
    return when (action) {
        is ActivityLifecycleAction.OnCreate ->
            state.copy(onCreateActionsCount = state.onCreateActionsCount.inc())
        ActivityLifecycleAction.OnStart ->
            state.copy(onStartActionsCount = state.onStartActionsCount.inc())
        ActivityLifecycleAction.OnResume ->
            state.copy(onResumeActionsCount = state.onResumeActionsCount.inc())
        is ActivityLifecycleAction.OnPause ->
            state.copy(onPauseActionsCount = state.onPauseActionsCount.inc())
        is ActivityLifecycleAction.OnStop ->
            state.copy(onStopActionsCount = state.onStopActionsCount.inc())
        is ActivityLifecycleAction.OnDestroy ->
            state.copy(onDestroyActionsCount = state.onDestroyActionsCount.inc())
    }
}
