package com.vsulimov.redux

import com.vsulimov.redux.action.AddReducer
import com.vsulimov.redux.action.TestAction
import com.vsulimov.redux.state.TestState
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the [TypedReducer] class, confirming it handles specific action types for state reduction.
 */
class TypedReducerTest {
    private val initialState = TestState(0)
    private lateinit var store: Store<TestState>

    @BeforeTest
    fun setup() {
        store = Store(initialState) { true }
    }

    /**
     * Checks that the typed reducer processes only the specified action type and updates the state accordingly.
     */
    @Test
    fun `should process only specified action type when invoked`() {
        val reducer = AddReducer()
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.Add(3))
        assertEquals(TestState(3), store.getState())
    }

    /**
     * Ensures that for actions not matching the specified type, the reducer returns the original state without changes.
     */
    @Test
    fun `should return original state for unmatched actions when invoked`() {
        val reducer = AddReducer()
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        assertEquals(initialState, store.getState())
    }
}
