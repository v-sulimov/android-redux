package com.vsulimov.redux.toolkit

import com.vsulimov.redux.Subscription

/**
 * Sub-state subscription allows subscribe to part of the application state
 * and filter out all subsequent repetitions of the same value for this sub-state.
 */
class SubStateSubscription<State, SubState>(
    private val transform: (State) -> SubState?,
    private val onStateChange: (SubState, isInitialState: Boolean) -> Unit
) : Subscription<State> {

    /**
     * Last received state to perform filtering.
     */
    private var lastReceivedState: SubState? = null

    /**
     * Override default [Subscription] logic, transform initial state
     * and filter out repetitions.
     */
    override fun invoke(state: State) {
        val transformedState = transform(state)
        val isInitialState = lastReceivedState == null
        if (transformedState != null && lastReceivedState != transformedState) {
            lastReceivedState = transformedState
            onStateChange(transformedState, isInitialState)
        }
    }
}
