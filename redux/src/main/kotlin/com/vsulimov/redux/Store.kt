package com.vsulimov.redux

import android.util.Log

/**
 * State change listener.
 */
typealias Subscription<State> = (State) -> Unit

/**
 * A store holds the whole state tree of your application.
 * The only way to change the state inside it is to dispatch an [Action] on it.
 */
interface Store<State> {

    /**
     * Returns the current state tree of your application.
     * It is equal to the last value returned by the store's reducer.
     */
    fun getState(): State

    /**
     * Dispatches an action. This is the only way to trigger a state change.
     * The store's reducing function will be called with the current [Store.getState] result
     * and the given action synchronously. Its return value will be considered the next state.
     * It will be returned from [Store.getState] from now on, and the change listeners
     * will immediately be notified.
     */
    fun dispatch(action: Action)

    /**
     * Adds a change listener. It will be called any time an action is dispatched,
     * and some part of the state tree may potentially have changed.
     */
    fun subscribe(subscription: Subscription<State>)

    /**
     * Remove given state change listener from the store.
     */
    fun unsubscribe(subscription: Subscription<State>)
}

/**
 * Base implementation of [Store] interface. You can extend this class in order to use
 * Redux architecture in your application.
 */
abstract class AbstractStore<State>(
    initialState: State,
    private val middlewares: List<Middleware<State>>,
    private val reducers: List<Reducer<State>>
) : Store<State> {

    /**
     * List with state change listeners which will be notified when a state change occurs.
     */
    internal val subscriptions = mutableListOf<Subscription<State>>()

    /**
     * List with state change listeners which were added during dispatch of the current state.
     */
    internal val pendingSubscriptions = mutableListOf<Subscription<State>>()

    /**
     * List with state change listeners which were removed during dispatch of the current state.
     */
    internal val pendingUnsubscribed = mutableListOf<Subscription<State>>()

    /**
     * Holds store current state.
     */
    private var currentState: State = initialState

    /**
     * Flag that indicates that the current state is currently dispatching.
     */
    private var isDispatching = false

    override fun getState() = currentState

    override fun dispatch(action: Action) {
        val newAction = applyMiddlewares(action, currentState)
        val newState = applyReducers(newAction, currentState)
        if (newState == currentState) {
            Log.w(TAG, "Same state after reduction $newAction.")
            return
        }
        currentState = newState
        isDispatching = true
        subscriptions.forEach {
            if (!pendingUnsubscribed.contains(it)) {
                it(currentState)
            }
        }
        isDispatching = false
        addPendingSubscriptions()
        removePendingUnsubscribed()
    }

    /**
     * Adds pending subscriptions to the main subscriptions list.
     */
    private fun addPendingSubscriptions() {
        subscriptions.addAll(pendingSubscriptions)
        pendingSubscriptions.clear()
    }

    private fun removePendingUnsubscribed() {
        subscriptions.removeAll(pendingUnsubscribed)
        pendingUnsubscribed.clear()
    }

    /**
     * Apply given action to every available middleware and returns the resulting action.
     */
    private fun applyMiddlewares(action: Action, state: State): Action {
        return next(0)(action, state)
    }

    /**
     * Apply given action to middleware with given index.
     */
    private fun next(index: Int): Next<State> {
        if (index == middlewares.size) {
            return { action, _ -> action }
        }
        return { action, state ->
            middlewares[index].handleAction(
                action,
                state,
                next(index = index + 1)
            )
        }
    }

    /**
     * Apply given action to every available reducer and returns the resulting state.
     */
    private fun applyReducers(action: Action, state: State): State {
        var newState = state
        for (reducer in reducers) {
            newState = reducer.reduce(action, newState)
        }
        return newState
    }

    override fun subscribe(subscription: Subscription<State>) {
        if (isDispatching) {
            pendingSubscriptions.add(subscription)
        } else {
            subscriptions.add(subscription)
        }
        subscription(currentState)
    }

    override fun unsubscribe(subscription: Subscription<State>) {
        if (isDispatching) {
            pendingUnsubscribed.add(subscription)
        } else {
            subscriptions.remove(subscription)
        }
        pendingSubscriptions.remove(subscription)
    }

    companion object {

        private const val TAG = "Store"
    }
}
