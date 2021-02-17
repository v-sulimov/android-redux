package com.vsulimov.redux

/**
 * Counter application state.
 */
data class ApplicationState(
    val counter: Int = 0
)

/**
 * Counter application store.
 */
class ApplicationStore(
    initialState: ApplicationState = ApplicationState(),
    middlewares: List<Middleware<ApplicationState>>,
    reducers: List<Reducer<ApplicationState>>
) : AbstractStore<ApplicationState>(initialState, middlewares, reducers)

sealed class CounterAction : Action {

    /**
     * Dispatch this to increment counter value.
     */
    object IncrementCounter : CounterAction()

    /**
     * Dispatch this to decrement counter value.
     */
    object DecrementCounter : CounterAction()

    /**
     * Dispatch this to explicitly set counter value.
     */
    data class SetCounterValue(val value: Int) : CounterAction()
}

sealed class MiddlewareTestAction : Action {

    object ActionForFirstMiddleware : MiddlewareTestAction()

    object ActionForSecondMiddleware : MiddlewareTestAction()

    object ActionForThirdMiddleware : MiddlewareTestAction()

    object ActionDispatchedFromThirdMiddleware : MiddlewareTestAction()

    object ActionForReducer : MiddlewareTestAction()
}
