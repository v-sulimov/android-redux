package com.vsulimov.redux

import com.vsulimov.redux.data.OtherAction
import com.vsulimov.redux.data.TestAction
import com.vsulimov.redux.data.TestState
import com.vsulimov.redux.exception.CalledFromWrongThreadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the Redux store functionality.
 * Tests cover state initialization, action dispatching, middleware interactions, reducer operations, thread safety,
 * and functionality for adding/removing middlewares with tags.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    private val initialState = TestState()
    private lateinit var store: Store<TestState>
    private lateinit var middleware: Middleware<TestState>
    private lateinit var reducer: Reducer<TestState>
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    /**
     * Sets up the test environment before each test.
     * Initializes the store, mocks, and sets the main dispatcher for coroutines.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        middleware = mock()
        reducer = mock()
        store = Store(initialState) { true }
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
     * Tests that the initial state is set correctly in the store.
     */
    @Test
    fun `initial state is set correctly`() {
        assertEquals(initialState, store.getState())
        assertEquals(initialState, store.stateFlow.value)
    }

    /**
     * Tests that the state flow emits the initial state.
     */
    @Test
    fun `state flow emits initial state`() = testScope.runTest {
        val states = mutableListOf<TestState>()
        val job = launch { store.stateFlow.toList(states) }
        advanceUntilIdle()
        assertEquals(listOf(initialState), states)
        job.cancel()
    }

    /**
     * Tests that dispatching an action updates the state via a reducer.
     */
    @Test
    fun `dispatching action updates state via reducer`() = testScope.runTest {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        val states = mutableListOf<TestState>()
        val job = launch(Dispatchers.Unconfined) { store.stateFlow.toList(states) }
        store.dispatch(TestAction())
        advanceUntilIdle()
        assertEquals(TestState(count = 1), store.getState())
        assertEquals(listOf(initialState, TestState(count = 1)), states)
        job.cancel()
    }

    /**
     * Tests that dispatching multiple actions updates the state sequentially.
     */
    @Test
    fun `dispatching multiple actions updates state sequentially`() = testScope.runTest {
        whenever(reducer.reduce(any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val state = invocation.getArgument<TestState>(1)
            if (action is TestAction) {
                state.copy(count = state.count + 1)
            } else {
                state
            }
        }
        store.addReducer(reducer)
        val states = mutableListOf<TestState>()
        val job = launch(Dispatchers.Unconfined) { store.stateFlow.toList(states) }
        store.dispatch(TestAction())
        store.dispatch(TestAction())
        advanceUntilIdle()
        assertEquals(TestState(count = 2), store.getState())
        assertEquals(listOf(TestState(count = 0), TestState(count = 1), TestState(count = 2)), states)
        job.cancel()
    }

    /**
     * Tests that actions dispatched from a middleware are processed sequentially.
     */
    @Test
    fun `actions dispatched from middleware are processed sequentially`() = testScope.runTest {
        val actionOrder = mutableListOf<String>()
        val middlewareA =
            Middleware {
                    action: Action,
                    _: TestState,
                    next: (
                        Action
                    ) -> Unit,
                    dispatch: (Action) -> Unit,
                    _: CoroutineScope
                ->
                if (action is TestAction && action.name == "A") {
                    actionOrder.add("middleware A")
                    dispatch(TestAction(name = "B"))
                }
                next(action)
            }
        val middlewareB =
            Middleware { action: Action, _: TestState, next: (Action) -> Unit, _: (Action) -> Unit, _: CoroutineScope ->
                if (action is TestAction && action.name == "B") {
                    actionOrder.add("middleware B")
                }
                next(action)
            }
        val reducer = Reducer { action: Action, state: TestState ->
            if (action is TestAction) {
                actionOrder.add("reducer ${action.name}")
                state.copy(count = state.count + 1)
            } else {
                state
            }
        }
        store.addMiddleware(middlewareA)
        store.addMiddleware(middlewareB)
        store.addReducer(reducer)
        store.dispatch(TestAction(name = "A"))
        advanceUntilIdle()
        assertEquals(listOf("middleware A", "reducer A", "middleware B", "reducer B"), actionOrder)
        assertEquals(2, store.getState().count)
    }

    /**
     * Tests that actions dispatched from a reducer are processed sequentially.
     */
    @Test
    fun `actions dispatched from reducer are processed sequentially`() = testScope.runTest {
        val actionOrder = mutableListOf<String>()
        val reducerA = Reducer { action: Action, state: TestState ->
            if (action is TestAction && action.name == "A") {
                actionOrder.add("reducer A")
                store.dispatch(TestAction(name = "B"))
                state.copy(count = state.count + 1)
            } else {
                state
            }
        }
        val reducerB = Reducer { action: Action, state: TestState ->
            if (action is TestAction && action.name == "B") {
                actionOrder.add("reducer B")
                state.copy(count = state.count + 1)
            } else {
                state
            }
        }
        store.addReducer(reducerA)
        store.addReducer(reducerB)
        store.dispatch(TestAction(name = "A"))
        advanceUntilIdle()
        assertEquals(listOf("reducer A", "reducer B"), actionOrder)
        assertEquals(2, store.getState().count)
    }

    /**
     * Tests that nested dispatches in middlewares are processed sequentially.
     */
    @Test
    fun `nested dispatches in middleware are processed sequentially`() = testScope.runTest {
        val actionOrder = mutableListOf<String>()
        val middlewareA =
            Middleware {
                    action: Action,
                    _: TestState,
                    next: (
                        Action
                    ) -> Unit,
                    dispatch: (Action) -> Unit,
                    _: CoroutineScope
                ->
                if (action is TestAction && action.name == "A") {
                    actionOrder.add("middleware A")
                    dispatch(TestAction(name = "B"))
                }
                next(action)
            }
        val middlewareB =
            Middleware {
                    action: Action,
                    _: TestState,
                    next: (
                        Action
                    ) -> Unit,
                    dispatch: (Action) -> Unit,
                    _: CoroutineScope
                ->
                if (action is TestAction && action.name == "B") {
                    actionOrder.add("middleware B")
                    dispatch(TestAction(name = "C"))
                }
                next(action)
            }
        val middlewareC =
            Middleware { action: Action, _: TestState, next: (Action) -> Unit, _: (Action) -> Unit, _: CoroutineScope ->
                if (action is TestAction && action.name == "C") {
                    actionOrder.add("middleware C")
                }
                next(action)
            }
        val reducer = Reducer { action: Action, state: TestState ->
            if (action is TestAction) {
                actionOrder.add("reducer ${action.name}")
                state.copy(count = state.count + 1)
            } else {
                state
            }
        }
        store.addMiddleware(middlewareA)
        store.addMiddleware(middlewareB)
        store.addMiddleware(middlewareC)
        store.addReducer(reducer)
        store.dispatch(TestAction(name = "A"))
        advanceUntilIdle()
        assertEquals(
            listOf("middleware A", "reducer A", "middleware B", "reducer B", "middleware C", "reducer C"),
            actionOrder
        )
        assertEquals(3, store.getState().count)
    }

    /**
     * Tests that a middleware is called during action dispatch.
     */
    @Test
    fun `middleware is called during dispatch`() {
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), eq(initialState), any(), any(), any())
    }

    /**
     * Tests that middlewares are called in the order of addition.
     */
    @Test
    fun `middlewares are called in order of addition`() {
        val middleware2 = mock<Middleware<TestState>>()
        val callOrder = mutableListOf<String>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            callOrder.add("middleware1")
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        whenever(middleware2.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            callOrder.add("middleware2")
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.addMiddleware(middleware2)
        store.dispatch(TestAction())
        assertEquals(listOf("middleware1", "middleware2"), callOrder)
    }

    /**
     * Tests that a middleware can transform an action.
     */
    @Test
    fun `middleware can transform action`() {
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction(increment = 2))
        }
        whenever(reducer.reduce(TestAction(increment = 2), initialState)).thenReturn(TestState(count = 2))
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 2), store.getState())
    }

    /**
     * Tests that a middleware can dispatch an additional action asynchronously.
     */
    @Test
    fun `middleware can dispatch additional action async`() = testScope.runTest {
        whenever(reducer.reduce(any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val state = invocation.getArgument<TestState>(1)
            if (action is OtherAction) {
                TestState(count = 1)
            } else {
                state
            }
        }
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val dispatch = invocation.getArgument<(Action) -> Unit>(3)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            val scope = invocation.getArgument<CoroutineScope>(4)
            if (action is TestAction) {
                scope.launch {
                    delay(100)
                    dispatch(OtherAction("async"))
                }
            }
            next(action)
        }
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction())
        advanceUntilIdle()
        assertEquals(TestState(count = 1), store.getState())
    }

    /**
     * Tests that removing a middleware stops it and cancels its coroutine scope.
     */
    @Test
    fun `removing middleware stops it and cancels its scope`() = testScope.runTest {
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
        verify(middleware, times(1)).invoke(eq(TestAction()), any(), any(), any(), any())
        store.removeMiddleware(middleware)
        advanceUntilIdle()
        assertTrue(asyncJob?.isCancelled == true, "Async job should be cancelled after middleware removal")
        store.dispatch(TestAction())
        verify(middleware, times(1)).invoke(any(), any(), any(), any(), any())
    }

    /**
     * Tests that a reducer is applied during action dispatch.
     */
    @Test
    fun `reducer is applied during dispatch`() {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 1), store.getState())
    }

    /**
     * Tests that reducers are applied in the order of addition.
     */
    @Test
    fun `reducers are applied in order of addition`() {
        val reducer2 = mock<Reducer<TestState>>()
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        whenever(reducer2.reduce(TestAction(), TestState(count = 1))).thenReturn(TestState(count = 2))
        store.addReducer(reducer)
        store.addReducer(reducer2)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 2), store.getState())
    }

    /**
     * Tests that removing a reducer stops it from being applied.
     */
    @Test
    fun `removing reducer stops it from being applied`() {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        store.removeReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(initialState, store.getState())
    }

    /**
     * Tests that dispatching an action on a non-main thread throws a CalledFromWrongThreadException.
     */
    @Test
    fun `dispatch throws CalledFromWrongThreadException on non-main thread`() {
        store = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            store.dispatch(TestAction())
        }
    }

    /**
     * Tests that adding a reducer on a non-main thread throws a CalledFromWrongThreadException.
     */
    @Test
    fun `addReducer throws CalledFromWrongThreadException on non-main thread`() {
        store = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            store.addReducer(reducer)
        }
    }

    /**
     * Tests that getState works on any thread.
     */
    @Test
    fun `getState works on any thread`() {
        store = Store(initialState) { false }
        assertEquals(initialState, store.getState())
    }

    /**
     * Tests that dispatching an action with no reducers does not change the state.
     */
    @Test
    fun `dispatch with no reducers does not change state`() {
        store.dispatch(TestAction())
        assertEquals(initialState, store.getState())
    }

    /**
     * Tests that dispatching an action with no middlewares goes directly to reducers.
     */
    @Test
    fun `dispatch with no middlewares goes directly to reducers`() {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 1), store.getState())
    }

    /**
     * Tests that adding a duplicate middleware applies it multiple times.
     */
    @Test
    fun `adding duplicate middleware applies it multiple times`() {
        val callCount = mutableListOf<Int>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            callCount.add(1)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        assertEquals(2, callCount.size)
    }

    /**
     * Tests that dispatching with no middlewares or reducers processes actions sequentially.
     */
    @Test
    fun `dispatch with no middlewares or reducers processes actions sequentially`() = testScope.runTest {
        val actionOrder = mutableListOf<String>()
        val middleware =
            Middleware {
                    action: Action,
                    _: TestState,
                    next: (
                        Action
                    ) -> Unit,
                    dispatch: (Action) -> Unit,
                    _: CoroutineScope
                ->
                if (action is TestAction) {
                    actionOrder.add("middleware ${action.name}")
                    if (action.name == "A") {
                        dispatch(TestAction(name = "B"))
                    }
                }
                next(action)
            }
        store.addMiddleware(middleware)
        store.dispatch(TestAction(name = "A"))
        advanceUntilIdle()
        assertEquals(listOf("middleware A", "middleware B"), actionOrder)
    }

    // New tests for tagged middleware functionality

    /**
     * Tests that middlewares added with a tag are called and can be removed by tag.
     */
    @Test
    fun `middlewares added with tag are called and can be removed by tag`() = testScope.runTest {
        val middleware1 = mock<Middleware<TestState>>()
        val middleware2 = mock<Middleware<TestState>>()
        whenever(middleware1.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        whenever(middleware2.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware1, "tag1")
        store.addMiddlewareWithTag(middleware2, "tag1")
        store.dispatch(TestAction())
        verify(middleware1).invoke(eq(TestAction()), any(), any(), any(), any())
        verify(middleware2).invoke(eq(TestAction()), any(), any(), any(), any())
        store.removeMiddlewaresByTag("tag1")
        store.dispatch(TestAction())
        verify(middleware1, times(1)).invoke(any(), any(), any(), any(), any())
        verify(middleware2, times(1)).invoke(any(), any(), any(), any(), any())
    }

    /**
     * Tests that untagged middlewares are not removed when removing by tag.
     */
    @Test
    fun `untagged middlewares are not removed by tag`() = testScope.runTest {
        val taggedMiddleware = mock<Middleware<TestState>>()
        val untaggedMiddleware = mock<Middleware<TestState>>()
        whenever(taggedMiddleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        whenever(untaggedMiddleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(taggedMiddleware, "tag1")
        store.addMiddleware(untaggedMiddleware)
        store.removeMiddlewaresByTag("tag1")
        store.dispatch(TestAction())
        verify(taggedMiddleware, times(0)).invoke(any(), any(), any(), any(), any())
        verify(untaggedMiddleware).invoke(eq(TestAction()), any(), any(), any(), any())
    }

    /**
     * Tests that removing middlewares by tag cancels their coroutine scopes.
     */
    @Test
    fun `removing middlewares by tag cancels their coroutine scopes`() = testScope.runTest {
        var job1: Job? = null
        var job2: Job? = null
        val middleware1 =
            Middleware { _: Action, _: TestState, next: (Action) -> Unit, _: (Action) -> Unit, scope: CoroutineScope ->
                job1 = scope.launch {
                    delay(1000)
                }
                next(TestAction())
            }
        val middleware2 =
            Middleware { _: Action, _: TestState, next: (Action) -> Unit, _: (Action) -> Unit, scope: CoroutineScope ->
                job2 = scope.launch {
                    delay(1000)
                }
                next(TestAction())
            }
        store.addMiddlewareWithTag(middleware1, "tag1")
        store.addMiddlewareWithTag(middleware2, "tag1")
        store.dispatch(TestAction())
        store.removeMiddlewaresByTag("tag1")
        advanceUntilIdle()
        assertTrue(job1?.isCancelled == true, "Coroutine job1 should be cancelled after middleware removal by tag")
        assertTrue(job2?.isCancelled == true, "Coroutine job2 should be cancelled after middleware removal by tag")
    }

    /**
     * Tests that removing middlewares with a non-existent tag does nothing.
     */
    @Test
    fun `removing middlewares with non-existent tag does nothing`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddleware(middleware)
        store.removeMiddlewaresByTag("non_existent_tag")
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), any(), any(), any(), any())
    }

    /**
     * Tests that addMiddlewareWithTag throws CalledFromWrongThreadException when called from a non-main thread.
     */
    @Test
    fun `addMiddlewareWithTag throws CalledFromWrongThreadException on non-main thread`() {
        store = Store(initialState) { false }
        val middleware = mock<Middleware<TestState>>()
        assertFailsWith<CalledFromWrongThreadException> {
            store.addMiddlewareWithTag(middleware, "tag1")
        }
    }

    /**
     * Tests that removeMiddlewaresByTag throws CalledFromWrongThreadException when called from a non-main thread.
     */
    @Test
    fun `removeMiddlewaresByTag throws CalledFromWrongThreadException on non-main thread`() {
        store = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            store.removeMiddlewaresByTag("tag1")
        }
    }

    /**
     * Tests that adding a middleware with an empty tag works and can be removed.
     */
    @Test
    fun `adding middleware with empty tag`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "")
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), any(), any(), any(), any())
        store.removeMiddlewaresByTag("")
        store.dispatch(TestAction())
        verify(middleware, times(1)).invoke(any(), any(), any(), any(), any())
    }

    /**
     * Tests that hasMiddlewaresForTag returns true for a tag with middlewares.
     */
    @Test
    fun `hasMiddlewaresForTag returns true for tag with middlewares`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "tag1")
        assertTrue(
            store.hasMiddlewaresForTag("tag1"),
            "hasMiddlewaresForTag should return true when tag exists with middlewares"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns false for a tag with no middlewares.
     */
    @Test
    fun `hasMiddlewaresForTag returns false for tag with no middlewares`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "tag1")
        store.removeMiddlewaresByTag("tag1")
        assertFalse(
            store.hasMiddlewaresForTag("tag1"),
            "hasMiddlewaresForTag should return false when tag exists but middlewares are empty"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns false for non-existent tag.
     */
    @Test
    fun `hasMiddlewaresForTag returns false for non-existent tag`() = testScope.runTest {
        assertFalse(
            store.hasMiddlewaresForTag("non_existent_tag"),
            "hasMiddlewaresForTag should return false for non-existent tag"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns true for empty tag with middlewares.
     */
    @Test
    fun `hasMiddlewaresForTag returns true for empty tag with middlewares`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "")
        assertTrue(
            store.hasMiddlewaresForTag(""),
            "hasMiddlewaresForTag should return true for empty tag with middlewares"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns false for empty tag with no middlewares.
     */
    @Test
    fun `hasMiddlewaresForTag returns false for empty tag with no middlewares`() = testScope.runTest {
        assertFalse(
            store.hasMiddlewaresForTag(""),
            "hasMiddlewaresForTag should return false for empty tag with no middlewares"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns true for tag with special characters.
     */
    @Test
    fun `hasMiddlewaresForTag returns true for tag with special characters`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "tag!@#")
        assertTrue(
            store.hasMiddlewaresForTag("tag!@#"),
            "hasMiddlewaresForTag should return true for tag with special characters when middlewares exist"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag is case-sensitive.
     */
    @Test
    fun `hasMiddlewaresForTag is case-sensitive`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "MyTag")
        assertFalse(
            store.hasMiddlewaresForTag("mytag"),
            "hasMiddlewaresForTag should return false for tag with different casing"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag throws exception on non-main thread.
     */
    @Test
    fun `hasMiddlewaresForTag throws exception on non-main thread`() {
        store = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            store.hasMiddlewaresForTag("tag1")
        }
    }

    /**
     * Tests that hasMiddlewaresForTag returns false after removing all middlewares for tag.
     */
    @Test
    fun `hasMiddlewaresForTag returns false after removing middlewares for tag`() = testScope.runTest {
        val middleware = mock<Middleware<TestState>>()
        whenever(middleware.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware, "tag1")
        store.removeMiddlewaresByTag("tag1")
        assertFalse(
            store.hasMiddlewaresForTag("tag1"),
            "hasMiddlewaresForTag should return false after removing all middlewares for tag"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns true for specific tag with multiple tags.
     */
    @Test
    fun `hasMiddlewaresForTag returns true for specific tag with multiple tags`() = testScope.runTest {
        val middleware1 = mock<Middleware<TestState>>()
        val middleware2 = mock<Middleware<TestState>>()
        whenever(middleware1.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        whenever(middleware2.invoke(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(invocation.getArgument(0))
        }
        store.addMiddlewareWithTag(middleware1, "tagA")
        store.addMiddlewareWithTag(middleware2, "tagB")
        assertTrue(
            store.hasMiddlewaresForTag("tagA"),
            "hasMiddlewaresForTag should return true for tagA with middlewares"
        )
        assertTrue(
            store.hasMiddlewaresForTag("tagB"),
            "hasMiddlewaresForTag should return true for tagB with middlewares"
        )
    }

    /**
     * Tests that hasMiddlewaresForTag returns false with no middlewares added.
     */
    @Test
    fun `hasMiddlewaresForTag returns false with no middlewares added`() = testScope.runTest {
        assertFalse(
            store.hasMiddlewaresForTag("anyTag"),
            "hasMiddlewaresForTag should return false when no middlewares are added"
        )
    }
}
