package com.vsulimov.redux

import com.vsulimov.redux.action.TestAction
import com.vsulimov.redux.action.TestReducer
import com.vsulimov.redux.exception.CalledFromWrongThreadException
import com.vsulimov.redux.state.TestState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the [Store] class, verifying its functionality in managing state, actions, middlewares, and reducers
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {
    private lateinit var store: Store<TestState>
    private val initialState = TestState(0)
    private val isMainThread: () -> Boolean = { true }
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = Store(initialState, isMainThread)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Verifies that the store returns the initial state upon creation
     */
    @Test
    fun `returns initial state when created`() {
        assertEquals(initialState, store.getState())
        assertEquals(initialState, store.stateFlow.value)
    }

    /**
     * Checks that the store updates its state when an action is dispatched through a reducer
     */
    @Test
    fun `updates state when action is dispatched through reducer`() {
        store.addReducer(TestReducer())
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(1), store.getState())
    }

    /**
     * Ensures that actions are processed in the order they are dispatched
     */
    @Test
    fun `processes actions sequentially`() {
        store.addReducer(TestReducer())
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.Add(2))
        assertEquals(TestState(3), store.getState())
    }

    /**
     * Verifies that the store's `stateFlow` emits all state changes when actions are dispatched
     */
    @Test
    fun `emits state changes when actions are dispatched`() = runTest(testDispatcher) {
        val states = mutableListOf<TestState>()
        store.addReducer(TestReducer())
        val job = launch { store.stateFlow.collect { states.add(it) } }
        advanceUntilIdle()
        store.dispatch(TestAction.Increment)
        advanceUntilIdle()
        store.dispatch(TestAction.Add(2))
        advanceUntilIdle()
        assertEquals(listOf(TestState(0), TestState(1), TestState(3)), states)
        job.cancel()
    }

    /**
     * Confirms that actions are passed through the middleware chain
     */
    @Test
    fun `passes action through middleware`() {
        val middleware = mock<Middleware<TestState>>()
        store.addMiddleware(middleware)
        store.dispatch(TestAction.Increment)
        verify(middleware).invoke(eq(TestAction.Increment), eq(initialState), any(), any(), any())
    }

    /**
     * Verifies that state remains unchanged when middleware does not call `next`
     */
    @Test
    fun `does not update state when middleware skips next`() {
        val middleware = mock<Middleware<TestState>>()
        val reducer = mock<Reducer<TestState>>()
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        verify(middleware).invoke(eq(TestAction.Increment), eq(initialState), any(), any(), any())
        verify(reducer, never()).reduce(any(), any())
        assertEquals(initialState, store.getState())
    }

    /**
     * Checks that state updates when middleware calls `next`
     */
    @Test
    fun `updates state when middleware calls next`() {
        val middleware = createPassingMiddleware()
        val reducer = mock<Reducer<TestState>>()
        whenever(reducer.reduce(any(), any())).thenReturn(TestState(1))
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(1), store.getState())
        verify(reducer).reduce(TestAction.Increment, initialState)
    }

    /**
     * Ensures that middleware can modify the action before it reaches reducers
     */
    @Test
    fun `allows middleware to modify action`() {
        val middleware = createModifyingMiddleware(TestAction.Add(5))
        val reducer = mock<Reducer<TestState>>()
        whenever(reducer.reduce(eq(TestAction.Add(5)), any())).thenReturn(TestState(5))
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(5), store.getState())
        verify(reducer).reduce(TestAction.Add(5), initialState)
    }

    /**
     * Verifies that middleware can dispatch additional actions that are processed by the store
     */
    @Test
    fun `processes additional actions dispatched by middleware`() {
        val middleware = createDispatchingMiddleware(TestAction.Add(10))
        store.addReducer(TestReducer())
        store.addMiddleware(middleware)
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(11), store.getState())
    }

    /**
     * Checks that the store handles asynchronous middleware operations like delayed action dispatching
     */
    @Test
    fun `handles async operations in middleware`() = runTest(testDispatcher) {
        val middleware = createAsyncDispatchingMiddleware(TestAction.Add(5))
        store.addReducer(TestReducer())
        store.addMiddleware(middleware)
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(1), store.getState())
        advanceUntilIdle()
        assertEquals(TestState(6), store.getState())
    }

    /**
     * Verifies that `dispatchToMiddlewares` processes actions through middlewares only
     */
    @Test
    fun `dispatches to middlewares only with dispatchToMiddlewares`() {
        val middleware = mock<Middleware<TestState>>()
        val reducer = mock<Reducer<TestState>>()
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatchToMiddlewares(TestAction.Increment)
        verify(middleware).invoke(eq(TestAction.Increment), eq(initialState), any(), any(), any())
        verify(reducer, never()).reduce(any(), any())
    }

    /**
     * Ensures that multiple reducers are applied in the order they were added
     */
    @Test
    fun `applies multiple reducers in order`() {
        val reducer1 = mock<Reducer<TestState>>()
        val reducer2 = mock<Reducer<TestState>>()
        whenever(reducer1.reduce(any(), eq(initialState))).thenReturn(TestState(1))
        whenever(reducer2.reduce(any(), eq(TestState(1)))).thenReturn(TestState(2))
        store.addReducer(reducer1)
        store.addReducer(reducer2)
        store.dispatch(TestAction.Increment)
        assertEquals(TestState(2), store.getState())
        verify(reducer1).reduce(TestAction.Increment, initialState)
        verify(reducer2).reduce(TestAction.Increment, TestState(1))
    }

    /**
     * Checks that `dispatchToReducers` bypasses middlewares and applies actions to reducers only
     */
    @Test
    fun `dispatches to reducers only with dispatchToReducers`() {
        val middleware = mock<Middleware<TestState>>()
        store.addMiddleware(middleware)
        store.addReducer(TestReducer())
        store.dispatchToReducers(TestAction.Increment)
        verify(middleware, never()).invoke(any(), any(), any(), any(), any())
        assertEquals(TestState(1), store.getState())
    }

    /**
     * Verifies that dispatching from a non-main thread throws `CalledFromWrongThreadException`
     */
    @Test
    fun `throws when dispatching from wrong thread`() {
        val wrongThreadStore = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            wrongThreadStore.dispatch(TestAction.Increment)
        }
    }

    /**
     * Verifies that adding middleware from a non-main thread throws `CalledFromWrongThreadException`
     */
    @Test
    fun `throws when adding middleware from wrong thread`() {
        val wrongThreadStore = Store(initialState) { false }
        val middleware = mock<Middleware<TestState>>()
        assertFailsWith<CalledFromWrongThreadException> {
            wrongThreadStore.addMiddleware(middleware)
        }
    }

    /**
     * Ensures that state remains unchanged when no reducers are present
     */
    @Test
    fun `does not change state without reducers`() {
        store.dispatch(TestAction.Increment)
        assertEquals(initialState, store.getState())
    }

    /**
     * Checks that removing a non-existent middleware does not throw an exception
     */
    @Test
    fun `does not throw when removing non-existent middleware`() {
        store.removeMiddleware(mock<Middleware<TestState>>())
    }

    /**
     * Checks that removing a non-existent reducer does not throw an exception
     */
    @Test
    fun `does not throw when removing non-existent reducer`() {
        store.removeReducer(mock<Reducer<TestState>>())
    }

    /**
     * Verifies that middlewares can be added and removed by tag
     */
    @Test
    fun `manages middlewares with tags`() {
        val middleware1 = mock<Middleware<TestState>>()
        val middleware2 = mock<Middleware<TestState>>()
        store.addMiddlewareWithTag(middleware1, "tag1")
        store.addMiddlewareWithTag(middleware2, "tag1")
        assertTrue(store.hasMiddlewaresForTag("tag1"))
        store.removeMiddlewaresByTag("tag1")
        assertFalse(store.hasMiddlewaresForTag("tag1"))
    }

    /**
     * Ensures that removed tagged middlewares do not process actions
     */
    @Test
    fun `does not process actions with removed tagged middlewares`() {
        val middleware = mock<Middleware<TestState>>()
        store.addMiddlewareWithTag(middleware, "tag1")
        store.removeMiddlewaresByTag("tag1")
        store.dispatch(TestAction.Increment)
        verify(middleware, never()).invoke(any(), any(), any(), any(), any())
    }

    private fun createPassingMiddleware(): Middleware<TestState> = mock {
        on { invoke(any(), any(), any(), any(), any()) } doAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(action)
        }
    }

    private fun createModifyingMiddleware(newAction: Action): Middleware<TestState> = mock {
        on { invoke(any(), any(), any(), any(), any()) } doAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(newAction)
        }
    }

    private fun createDispatchingMiddleware(additionalAction: Action): Middleware<TestState> = mock {
        on { invoke(any(), any(), any(), any(), any()) } doAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val dispatch = invocation.getArgument<(Action) -> Unit>(3)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            if (action == TestAction.Increment) {
                dispatch(additionalAction)
                next(action)
            } else {
                next(action)
            }
        }
    }

    private fun createAsyncDispatchingMiddleware(additionalAction: Action): Middleware<TestState> = mock {
        on { invoke(any(), any(), any(), any(), any()) } doAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val dispatch = invocation.getArgument<(Action) -> Unit>(3)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            val scope = invocation.getArgument<CoroutineScope>(4)
            if (action == TestAction.Increment) {
                scope.launch {
                    delay(100)
                    dispatch(additionalAction)
                }
                next(action)
            } else {
                next(action)
            }
        }
    }
}
