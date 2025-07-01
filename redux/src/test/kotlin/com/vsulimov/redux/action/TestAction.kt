package com.vsulimov.redux.action

import com.vsulimov.redux.Action
import com.vsulimov.redux.Reducer
import com.vsulimov.redux.TypedReducer
import com.vsulimov.redux.state.TestState

/**
 * Sealed class defining possible actions for testing the Redux system.
 */
sealed class TestAction : Action {
    /**
     * An action to increment the counter by 1.
     */
    data object Increment : TestAction()

    /**
     * An action to add a specific value to the counter.
     *
     * @property value The integer value to add to the counter.
     */
    data class Add(val value: Int) : TestAction()
}

/**
 * A test reducer that handles [TestAction.Increment] and [TestAction.Add] actions.
 */
class TestReducer : Reducer<TestState> {
    override fun reduce(action: Action, state: TestState): TestState = when (action) {
        is TestAction.Increment -> state.copy(counter = state.counter + 1)
        is TestAction.Add -> state.copy(counter = state.counter + action.value)
        else -> state
    }
}

/**
 * A typed reducer that only handles [TestAction.Add] actions.
 */
class AddReducer : TypedReducer<TestAction.Add, TestState>(TestAction.Add::class.java) {
    override fun reduceTyped(action: TestAction.Add, state: TestState): TestState =
        state.copy(counter = state.counter + action.value)
}
