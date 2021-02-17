package com.vsulimov.redux

/**
 * Reducers specify how the application's state changes in response to [Action] sent to the store.
 * Remember that actions only describe what happened,
 * but don't describe how the application's state changes.
 */
interface Reducer<State> {

    /**
     * Handle incoming action and returns the resulting state.
     */
    fun reduce(action: Action, state: State): State
}
