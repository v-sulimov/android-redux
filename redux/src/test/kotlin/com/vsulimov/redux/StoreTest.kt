package com.vsulimov.redux

import com.vsulimov.redux.data.OtherAction
import com.vsulimov.redux.data.TestAction
import com.vsulimov.redux.data.TestState
import com.vsulimov.redux.exception.CalledFromWrongThreadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    private val initialState = TestState()
    private lateinit var store: Store<TestState>
    private lateinit var middleware: Middleware<TestState>
    private lateinit var reducer: Reducer<TestState>

    @Before
    fun setUp() {
        middleware = mock()
        reducer = mock()
        // Mock isMainThread to always return true by default
        store = Store(initialState) { true }
    }

    // region Initialization Tests
    @Test
    fun `initial state is set correctly`() {
        assertEquals(initialState, store.getState())
        assertEquals(initialState, store.stateFlow.value)
    }

    @Test
    fun `state flow emits initial state`() = runTest {
        val states = mutableListOf<TestState>()
        val job = launch { store.stateFlow.toList(states) }
        advanceUntilIdle()
        assertEquals(listOf(initialState), states)
        job.cancel()
    }
    // endregion

    // region Dispatching Action Tests
    @Test
    fun `dispatching action updates state via reducer`() = runTest {
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

    @Test
    fun `dispatching multiple actions updates state sequentially`() = runTest {
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

    @Test
    fun `actions dispatched from middleware are processed sequentially`() = runTest {
        val actionOrder = mutableListOf<String>()
        val middlewareA = Middleware { action: Action, _: TestState, next: (Action) -> Unit, dispatch: (Action) -> Unit ->
            if (action is TestAction && action.name == "A") {
                actionOrder.add("middleware A")
                dispatch(TestAction(name = "B"))
            }
            next(action)
        }
        val middlewareB = Middleware { action: Action, _: TestState, next: (Action) -> Unit, _: (Action) -> Unit ->
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

    @Test
    fun `actions dispatched from reducer are processed sequentially`() = runTest {
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

    @Test
    fun `nested dispatches in middleware are processed sequentially`() = runTest {
        val actionOrder = mutableListOf<String>()
        val middlewareA = Middleware { action: Action, _: TestState, next: (Action) -> Unit, dispatch: (Action) -> Unit ->
            if (action is TestAction && action.name == "A") {
                actionOrder.add("middleware A")
                dispatch(TestAction(name = "B"))
            }
            next(action)
        }
        val middlewareB = Middleware { action: Action, _: TestState, next: (Action) -> Unit, dispatch: (Action) -> Unit ->
            if (action is TestAction && action.name == "B") {
                actionOrder.add("middleware B")
                dispatch(TestAction(name = "C"))
            }
            next(action)
        }
        val middlewareC = Middleware { action: Action, _: TestState, next: (Action) -> Unit, _: (Action) -> Unit ->
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
        assertEquals(listOf("middleware A", "reducer A", "middleware B", "reducer B", "middleware C", "reducer C"), actionOrder)
        assertEquals(3, store.getState().count)
    }
    // endregion

    // region Middleware Tests within Store
    @Test
    fun `middleware is called during dispatch`() {
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), eq(initialState), any(), any())
    }

    @Test
    fun `middlewares are called in order of addition`() {
        val middleware2 = mock<Middleware<TestState>>()
        val callOrder = mutableListOf<String>()
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            callOrder.add("middleware1")
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        whenever(middleware2.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            callOrder.add("middleware2")
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.addMiddleware(middleware2)
        store.dispatch(TestAction())
        assertEquals(listOf("middleware1", "middleware2"), callOrder)
    }

    @Test
    fun `middleware can transform action`() {
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction(increment = 2)) // Transform action
        }
        whenever(reducer.reduce(TestAction(increment = 2), initialState)).thenReturn(TestState(count = 2))
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 2), store.getState())
    }

    @Test
    fun `middleware can dispatch additional action`() = runTest {
        whenever(reducer.reduce(any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0)
            val state = invocation.getArgument<TestState>(1)
            if (action is OtherAction) {
                TestState(count = 1)
            } else {
                state
            }
        }

        val middleware =
            Middleware { action: Action, _: TestState, next: (Action) -> Unit, dispatch: (Action) -> Unit ->
                if (action is TestAction) {
                    dispatch(OtherAction("extra"))
                }
                next(action)
            }

        store.addMiddleware(middleware)
        store.addReducer(reducer)

        store.dispatch(TestAction())
        advanceUntilIdle()

        assertEquals(TestState(count = 1), store.getState())
    }

    @Test
    fun `removing middleware stops it from being called`() {
        store.addMiddleware(middleware)
        store.removeMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware, never()).invoke(any(), any(), any(), any())
    }
    // endregion

    // region Reducer Tests within Store
    @Test
    fun `reducer is applied during dispatch`() {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 1), store.getState())
    }

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

    @Test
    fun `removing reducer stops it from being applied`() {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        store.removeReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(initialState, store.getState())
    }
    // endregion

    // region Thread Safety Tests
    @Test
    fun `dispatch throws CalledFromWrongThreadException on non-main thread`() {
        store = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            store.dispatch(TestAction())
        }
    }

    @Test
    fun `addReducer throws CalledFromWrongThreadException on non-main thread`() {
        store = Store(initialState) { false }
        assertFailsWith<CalledFromWrongThreadException> {
            store.addReducer(reducer)
        }
    }

    @Test
    fun `getState works on any thread`() {
        store = Store(initialState) { false }
        assertEquals(initialState, store.getState()) // Should not throw
    }
    // endregion

    // region Edge Case Tests
    @Test
    fun `dispatch with no reducers does not change state`() {
        store.dispatch(TestAction())
        assertEquals(initialState, store.getState())
    }

    @Test
    fun `dispatch with no middlewares goes directly to reducers`() {
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(TestState(count = 1), store.getState())
    }

    @Test
    fun `adding duplicate middleware applies it multiple times`() {
        val callCount = mutableListOf<Int>()
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            callCount.add(1)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        assertEquals(2, callCount.size)
    }

    @Test
    fun `dispatch with no middlewares or reducers processes actions sequentially`() = runTest {
        val actionOrder = mutableListOf<String>()
        val middleware = Middleware { action: Action, _: TestState, next: (Action) -> Unit, dispatch: (Action) -> Unit ->
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
    // endregion
}
