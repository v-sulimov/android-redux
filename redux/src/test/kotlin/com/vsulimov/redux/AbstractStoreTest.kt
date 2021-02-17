package com.vsulimov.redux

import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
internal class AbstractStoreTest {

    private lateinit var store: ApplicationStore

    private var firstMiddlewareCallTimestamp = 0L
    private var firstMiddlewareReceivedAction: Action? = null

    private var secondMiddlewareCallTimestamp = 0L
    private var secondMiddlewareReceivedAction: Action? = null

    private var reducerCallTimestamp = 0L
    private var reducerReceivedAction: Action? = null

    private var reducerReceiveInitialActionTimestamp = 0L
    private var reducerReceiveDispatchedActionTimestamp = 0L

    inner class FirstMiddleware : Middleware<ApplicationState> {

        override fun handleAction(
            action: Action,
            state: ApplicationState,
            next: Next<ApplicationState>
        ): Action {
            firstMiddlewareCallTimestamp = System.nanoTime()
            firstMiddlewareReceivedAction = action
            val nextAction = if (action is MiddlewareTestAction.ActionForFirstMiddleware) {
                MiddlewareTestAction.ActionForSecondMiddleware
            } else {
                action
            }
            return next(nextAction, state)
        }
    }

    inner class SecondMiddleware : Middleware<ApplicationState> {

        override fun handleAction(
            action: Action,
            state: ApplicationState,
            next: Next<ApplicationState>
        ): Action {
            secondMiddlewareCallTimestamp = System.nanoTime()
            secondMiddlewareReceivedAction = action
            val nextAction = if (action is MiddlewareTestAction.ActionForSecondMiddleware) {
                MiddlewareTestAction.ActionForReducer
            } else {
                action
            }
            return next(nextAction, state)
        }
    }

    inner class ThirdMiddleware : Middleware<ApplicationState> {

        override fun handleAction(
            action: Action,
            state: ApplicationState,
            next: Next<ApplicationState>
        ): Action {
            if (action is MiddlewareTestAction.ActionForThirdMiddleware) {
                store.dispatch(MiddlewareTestAction.ActionDispatchedFromThirdMiddleware)
            }
            return next(action, state)
        }
    }

    inner class CounterReducer : Reducer<ApplicationState> {

        override fun reduce(action: Action, state: ApplicationState): ApplicationState {
            reducerCallTimestamp = System.nanoTime()
            reducerReceivedAction = action
            return when (action) {
                is CounterAction.IncrementCounter ->
                    state.copy(counter = state.counter.inc())

                is CounterAction.DecrementCounter ->
                    state.copy(counter = state.counter.dec())

                is CounterAction.SetCounterValue ->
                    state.copy(counter = action.value)

                is MiddlewareTestAction.ActionForThirdMiddleware -> {
                    reducerReceiveInitialActionTimestamp = System.nanoTime()
                    state
                }

                is MiddlewareTestAction.ActionDispatchedFromThirdMiddleware -> {
                    reducerReceiveDispatchedActionTimestamp = System.nanoTime()
                    state
                }

                else ->
                    state
            }
        }
    }

    @Before
    fun setUp() {
        store = ApplicationStore(
            middlewares = listOf(FirstMiddleware(), SecondMiddleware(), ThirdMiddleware()),
            reducers = listOf(CounterReducer())
        )
        firstMiddlewareCallTimestamp = 0L
        firstMiddlewareReceivedAction = null
        secondMiddlewareCallTimestamp = 0L
        secondMiddlewareReceivedAction = null
        reducerCallTimestamp = 0L
        reducerReceivedAction = null
        reducerReceiveInitialActionTimestamp = 0L
        reducerReceiveDispatchedActionTimestamp = 0L
    }

    @Test
    fun `handleStateChange should be called when new subscriber is added to the store`() {
        var stateChangeCallsCount = 0
        store.subscribe { stateChangeCallsCount++ }
        assert(stateChangeCallsCount == 1)
    }

    @Test
    fun `handleStateChange should be called for existing subscribers when state changed`() {
        var stateChangeCallsCount = 0
        store.subscribe { stateChangeCallsCount++ }
        store.dispatch(CounterAction.IncrementCounter)
        assert(stateChangeCallsCount == 2)
    }

    @Test
    fun `new subscriber should receive the state equal to the state from store getState`() {
        store.subscribe { state -> assert(state == store.getState()) }
    }

    @Test
    fun `existing subscribers should receive the state equal to the state from store getState`() {
        store.subscribe { state -> assert(state == store.getState()) }
        store.dispatch(CounterAction.IncrementCounter)
        store.dispatch(CounterAction.DecrementCounter)
    }

    @Test
    fun `handleStateChange should not be called for unsubscribed state change listeners`() {
        var stateChangeCallsCount = 0
        val subscriber = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                stateChangeCallsCount++
            }
        }
        store.subscribe(subscriber)
        store.unsubscribe(subscriber)
        store.dispatch(CounterAction.IncrementCounter)
        assert(stateChangeCallsCount == 1)
    }

    @Test
    fun `handleStateChange should not be called if state is the same after reduction`() {
        var stateChangeCallsCount = 0
        store.subscribe { stateChangeCallsCount++ }
        store.dispatch(CounterAction.SetCounterValue(value = 0))
        assert(stateChangeCallsCount == 1)
    }

    @Test
    fun `first middleware should be called before second`() {
        store.dispatch(CounterAction.IncrementCounter)
        assert(firstMiddlewareCallTimestamp < secondMiddlewareCallTimestamp)
    }

    @Test
    fun `second middleware should be called before reducer`() {
        store.dispatch(CounterAction.IncrementCounter)
        assert(secondMiddlewareCallTimestamp < reducerCallTimestamp)
    }

    @Test
    fun `first middleware should receive dispatched action`() {
        store.dispatch(CounterAction.IncrementCounter)
        assert(firstMiddlewareReceivedAction == CounterAction.IncrementCounter)
    }

    @Test
    fun `second middleware should receive an action returned by the first middleware`() {
        store.dispatch(MiddlewareTestAction.ActionForFirstMiddleware)
        assert(secondMiddlewareReceivedAction == MiddlewareTestAction.ActionForSecondMiddleware)
    }

    @Test
    fun `reducer should receive an action returned by the second middleware`() {
        store.dispatch(MiddlewareTestAction.ActionForFirstMiddleware)
        assert(reducerReceivedAction == MiddlewareTestAction.ActionForReducer)
    }

    @Test
    fun `reducer should receive dispatched action if none of the middlewares handles it`() {
        store.dispatch(CounterAction.IncrementCounter)
        assert(reducerReceivedAction == CounterAction.IncrementCounter)
    }

    @Test
    fun `dispatched from middleware action should be processed before initial`() {
        store.dispatch(MiddlewareTestAction.ActionForThirdMiddleware)
        assert(reducerReceiveDispatchedActionTimestamp < reducerReceiveInitialActionTimestamp)
    }

    @Test
    fun `subscribe during dispatch scenario`() {
        var firstStateChangeCallsCount = 0
        var secondStateChangeCallsCount = 0
        var thirdStateChangeCallsCount = 0

        val thirdSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                thirdStateChangeCallsCount++
            }
        }
        val secondSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                secondStateChangeCallsCount++
                if (state.counter == 2) {
                    store.subscribe(thirdSubscription)
                    assert(store.subscriptions.size == 2)
                    assert(store.pendingSubscriptions.size == 1)
                }
            }
        }
        val firstSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                firstStateChangeCallsCount++
                if (state.counter == 1) {
                    store.subscribe(secondSubscription)
                    assert(store.subscriptions.size == 1)
                    assert(store.pendingSubscriptions.size == 1)
                }
            }
        }

        store.subscribe(firstSubscription)

        assert(firstStateChangeCallsCount == 1)
        assert(secondStateChangeCallsCount == 0)
        assert(thirdStateChangeCallsCount == 0)

        store.dispatch(CounterAction.IncrementCounter)

        assert(store.subscriptions.size == 2)
        assert(store.pendingSubscriptions.size == 0)

        assert(firstStateChangeCallsCount == 2)
        assert(secondStateChangeCallsCount == 1)
        assert(thirdStateChangeCallsCount == 0)

        store.dispatch(CounterAction.IncrementCounter)

        assert(store.subscriptions.size == 3)
        assert(store.pendingSubscriptions.size == 0)

        assert(firstStateChangeCallsCount == 3)
        assert(secondStateChangeCallsCount == 2)
        assert(thirdStateChangeCallsCount == 1)
    }

    @Test
    fun `unsubscribe during dispatch scenario`() {
        var firstStateChangeCallsCount = 0
        var secondStateChangeCallsCount = 0
        var thirdStateChangeCallsCount = 0
        var fourthStateChangeCallsCount = 0

        val fourthSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                fourthStateChangeCallsCount++
            }
        }
        val thirdSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                thirdStateChangeCallsCount++
            }
        }
        val secondSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                secondStateChangeCallsCount++
                if (state.counter == 1) {
                    store.unsubscribe(thirdSubscription)
                    assert(store.subscriptions.size == 4)
                    assert(store.pendingUnsubscribed.size == 1)
                }
            }
        }
        val firstSubscription = object : Subscription<ApplicationState> {
            override fun invoke(state: ApplicationState) {
                firstStateChangeCallsCount++
                if (state.counter == 2) {
                    store.unsubscribe(secondSubscription)
                    assert(store.subscriptions.size == 3)
                    assert(store.pendingUnsubscribed.size == 1)
                }
            }
        }

        store.subscribe(firstSubscription)
        store.subscribe(secondSubscription)
        store.subscribe(thirdSubscription)
        store.subscribe(fourthSubscription)

        assert(firstStateChangeCallsCount == 1)
        assert(secondStateChangeCallsCount == 1)
        assert(thirdStateChangeCallsCount == 1)
        assert(fourthStateChangeCallsCount == 1)

        store.dispatch(CounterAction.IncrementCounter)

        assert(store.subscriptions.size == 3)
        assert(store.pendingUnsubscribed.size == 0)

        assert(firstStateChangeCallsCount == 2)
        assert(secondStateChangeCallsCount == 2)
        assert(thirdStateChangeCallsCount == 1)
        assert(fourthStateChangeCallsCount == 2)

        store.dispatch(CounterAction.IncrementCounter)

        assert(store.subscriptions.size == 2)
        assert(store.pendingUnsubscribed.size == 0)

        assert(firstStateChangeCallsCount == 3)
        assert(secondStateChangeCallsCount == 2)
        assert(thirdStateChangeCallsCount == 1)
        assert(fourthStateChangeCallsCount == 3)
    }
}
