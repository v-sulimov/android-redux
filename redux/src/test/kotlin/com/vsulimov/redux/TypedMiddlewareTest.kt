package com.vsulimov.redux

import com.vsulimov.redux.action.TestAction
import com.vsulimov.redux.action.TestReducer
import com.vsulimov.redux.middleware.DoubleAddMiddleware
import com.vsulimov.redux.state.TestState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for the [TypedMiddleware] class, ensuring it handles specific action types correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TypedMiddlewareTest {
    private val initialState = TestState(0)
    private lateinit var store: Store<TestState>

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        store = Store(initialState) { true }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Verifies that the typed middleware processes only the specified action type and modifies it accordingly.
     */
    @Test
    fun `should process only specified action type when invoked`() {
        val middleware = DoubleAddMiddleware()
        val reducer = TestReducer()
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.Add(3))
        assertEquals(TestState(7), store.getState()) // 1 + 3*2
    }

    /**
     * Ensures that actions not matching the specified type are passed through without modification.
     */
    @Test
    fun `should pass through unmatched actions when invoked`() {
        val middleware = DoubleAddMiddleware()
        val reducer = TestReducer()
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(1), store.getState())
    }
}
