package com.vsulimov.redux

import com.vsulimov.redux.data.OtherAction
import com.vsulimov.redux.data.TestAction
import com.vsulimov.redux.data.TestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for middleware functionality in the Redux store.
 * Tests cover action processing, async operations, and typed middleware behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiddlewareTest {

    private lateinit var store: Store<TestState>
    private lateinit var middleware: Middleware<TestState>
    private lateinit var reducer: Reducer<TestState>
    private val initialState = TestState()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    /**
     * Sets up the test environment before each test.
     * Initializes the store, mocks, and sets the main dispatcher for coroutines.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = Store(initialState) { true }
        middleware = mock()
        reducer = mock()
    }

    /**
     * Cleans up the test environment after each test.
     * Resets the main dispatcher and cancels the test coroutine scope.
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cancel()
    }

    /**
     * Tests that a middleware receives the correct action, state, and scope when an action is dispatched.
     */
    @Test
    fun `middleware receives correct action, state, and scope`() {
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), eq(initialState), any(), any(), any<CoroutineScope>())
    }

    /**
     * Tests that not calling the next dispatcher in a middleware blocks the action from reaching reducers.
     */
    @Test
    fun `not calling next blocks action from reaching reducers`() {
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { /* Do nothing */ }
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(initialState, store.getState())
    }

    /**
     * Tests that a middleware can dispatch an additional action during processing.
     */
    @Test
    fun `middleware dispatching additional action works`() {
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val dispatch = invocation.getArgument<(Action) -> Unit>(3)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            if (action is TestAction) {
                dispatch(OtherAction("extra"))
            }
            next(action)
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), any(), any(), any(), any())
        verify(middleware).invoke(eq(OtherAction("extra")), any(), any(), any(), any())
    }

    /**
     * Tests that a middleware can launch an asynchronous operation within its coroutine scope.
     */
    @Test
    fun `middleware can launch async operation in scope`() = testScope.runTest {
        var asyncJob: Job? = null
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val dispatch = invocation.getArgument<(Action) -> Unit>(3)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            val scope = invocation.getArgument<CoroutineScope>(4)
            if (action is TestAction) {
                asyncJob = scope.launch {
                    delay(100)
                    dispatch(OtherAction("async"))
                }
            }
            next(action)
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        advanceUntilIdle()
        verify(middleware).invoke(eq(TestAction()), any(), any(), any(), any())
        verify(middleware).invoke(eq(OtherAction("async")), any(), any(), any(), any())
        assertTrue(asyncJob?.isCompleted == true, "Async job should complete")
    }

    /**
     * Tests that a middleware's coroutine scope is cancelled when the middleware is removed.
     */
    @Test
    fun `middleware scope is cancelled on removal`() = testScope.runTest {
        var asyncJob: Job? = null
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            val scope = invocation.getArgument<CoroutineScope>(4)
            if (action is TestAction) {
                asyncJob = scope.launch {
                    delay(1000)
                }
            }
            next(action)
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        store.removeMiddleware(middleware)
        advanceUntilIdle()
        assertTrue(asyncJob?.isCancelled == true, "Async job should be cancelled after middleware removal")
    }

    /**
     * Tests that a TypedMiddleware processes only actions of the specified type.
     */
    @Test
    fun `TypedMiddleware processes only specified action type`() {
        val typedMiddleware = object : TypedMiddleware<TestAction, TestState>(TestAction::class.java) {
            override fun invokeTyped(
                action: TestAction,
                state: TestState,
                next: (Action) -> Unit,
                dispatch: (Action) -> Unit,
                scope: CoroutineScope
            ) {
                next(TestAction(increment = 2))
            }
        }
        whenever(reducer.reduce(TestAction(increment = 2), initialState)).thenReturn(TestState(count = 2))
        store.addMiddleware(typedMiddleware)
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 2), store.getState())
    }

    /**
     * Tests that a TypedMiddleware passes actions of other types unchanged.
     */
    @Test
    fun `TypedMiddleware passes other actions unchanged`() {
        val typedMiddleware = object : TypedMiddleware<TestAction, TestState>(TestAction::class.java) {
            override fun invokeTyped(
                action: TestAction,
                state: TestState,
                next: (Action) -> Unit,
                dispatch: (Action) -> Unit,
                scope: CoroutineScope
            ) {
                next(action)
            }
        }
        store.addMiddleware(typedMiddleware)
        store.dispatch(OtherAction("other"))
        verifyNoInteractions(reducer)
    }

    /**
     * Tests that a TypedMiddleware can launch an asynchronous operation within its coroutine scope.
     */
    @Test
    fun `TypedMiddleware can launch async operation in scope`() = testScope.runTest {
        var asyncJob: Job? = null
        val typedMiddleware = object : TypedMiddleware<TestAction, TestState>(TestAction::class.java) {
            override fun invokeTyped(
                action: TestAction,
                state: TestState,
                next: (Action) -> Unit,
                dispatch: (Action) -> Unit,
                scope: CoroutineScope
            ) {
                asyncJob = scope.launch {
                    delay(100)
                    dispatch(OtherAction("async"))
                }
                next(action)
            }
        }
        store.addMiddleware(typedMiddleware)
        store.dispatch(TestAction())
        advanceUntilIdle()
        assertTrue(asyncJob?.isCompleted == true, "Async job should complete")
    }
}
