package com.vsulimov.redux

import com.vsulimov.redux.data.OtherAction
import com.vsulimov.redux.data.TestAction
import com.vsulimov.redux.data.TestState
import org.junit.Before
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class MiddlewareTest {

    private lateinit var store: Store<TestState>
    private lateinit var middleware: Middleware<TestState>
    private lateinit var reducer: Reducer<TestState>
    private val initialState = TestState()

    @Before
    fun setUp() {
        store = Store(initialState) { true }
        middleware = mock()
        reducer = mock()
    }

    // region General Middleware Tests
    @Test
    fun `middleware receives correct action and state`() {
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            val next = invocation.getArgument<(Action) -> Unit>(2)
            next(TestAction())
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), eq(initialState), any(), any())
    }

    @Test
    fun `not calling next blocks action from reaching reducers`() {
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { /* Do nothing */ }
        whenever(reducer.reduce(TestAction(), initialState)).thenReturn(TestState(count = 1))
        store.addMiddleware(middleware)
        store.addReducer(reducer)
        store.dispatch(TestAction())
        assertEquals(initialState, store.getState())
    }

    @Test
    fun `middleware dispatching additional action works`() {
        whenever(middleware.invoke(any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(0) // Get the current action
            val dispatch = invocation.getArgument<(Action) -> Unit>(3)
            val next = invocation.getArgument<(Action) -> Unit>(2)
            if (action is TestAction) { // Only dispatch for TestAction
                dispatch(OtherAction("extra"))
            }
            next(action) // Always pass the current action forward
        }
        store.addMiddleware(middleware)
        store.dispatch(TestAction())
        verify(middleware).invoke(eq(TestAction()), any(), any(), any())
        verify(middleware).invoke(eq(OtherAction("extra")), any(), any(), any())
    }
    // endregion

    // region TypedMiddleware Tests
    @Test
    fun `TypedMiddleware processes only specified action type`() {
        val typedMiddleware = object : TypedMiddleware<TestAction, TestState>(TestAction::class.java) {
            override fun invokeTyped(
                action: TestAction,
                state: TestState,
                next: (Action) -> Unit,
                dispatch: (Action) -> Unit
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

    @Test
    fun `TypedMiddleware passes other actions unchanged`() {
        val typedMiddleware = object : TypedMiddleware<TestAction, TestState>(TestAction::class.java) {
            override fun invokeTyped(
                action: TestAction,
                state: TestState,
                next: (Action) -> Unit,
                dispatch: (Action) -> Unit
            ) {
                next(action)
            }
        }
        store.addMiddleware(typedMiddleware)
        store.dispatch(OtherAction("other"))
        verifyNoInteractions(reducer) // No reducer to handle OtherAction
    }
    // endregion


}
