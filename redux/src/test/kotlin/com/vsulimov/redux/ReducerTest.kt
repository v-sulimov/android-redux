package com.vsulimov.redux

import com.vsulimov.redux.data.OtherAction
import com.vsulimov.redux.data.TestAction
import com.vsulimov.redux.data.TestState
import kotlin.test.Test
import kotlin.test.assertEquals

class ReducerTest {

    private lateinit var reducer: Reducer<TestState>
    private val initialState = TestState()

    @Test
    fun `reducer computes new state for known action`() {
        reducer = Reducer { action, state ->
            if (action is TestAction) state.copy(count = state.count + action.increment) else state
        }
        val newState = reducer.reduce(TestAction(), initialState)
        assertEquals(TestState(count = 1), newState)
    }

    @Test
    fun `reducer returns original state for unknown action`() {
        reducer = Reducer { action, state ->
            if (action is TestAction) state.copy(count = state.count + action.increment) else state
        }
        val newState = reducer.reduce(OtherAction(), initialState)
        assertEquals(initialState, newState)
    }

    @Test
    fun `reducer does not mutate input state`() {
        reducer = Reducer { action, state ->
            if (action is TestAction) state.copy(count = state.count + action.increment) else state
        }
        val stateBefore = initialState
        reducer.reduce(TestAction(), stateBefore)
        assertEquals(initialState, stateBefore)
    }

    @Test
    fun `TypedReducer handles only specified action type`() {
        val typedReducer = object : TypedReducer<TestAction, TestState>(TestAction::class.java) {
            override fun reduceTyped(action: TestAction, state: TestState) =
                state.copy(count = state.count + action.increment)
        }
        val newState = typedReducer.reduce(TestAction(), initialState)
        assertEquals(TestState(count = 1), newState)
    }

    @Test
    fun `TypedReducer returns original state for other actions`() {
        val typedReducer = object : TypedReducer<TestAction, TestState>(TestAction::class.java) {
            override fun reduceTyped(action: TestAction, state: TestState) =
                state.copy(count = state.count + action.increment)
        }
        val newState = typedReducer.reduce(OtherAction(), initialState)
        assertEquals(initialState, newState)
    }
}
