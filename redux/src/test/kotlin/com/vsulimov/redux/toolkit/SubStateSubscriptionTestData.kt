package com.vsulimov.redux.toolkit

import com.vsulimov.redux.AbstractStore
import com.vsulimov.redux.Action
import com.vsulimov.redux.Middleware
import com.vsulimov.redux.Reducer

data class ApplicationState(
    val number: Int? = 0,
    val flag: Boolean? = false,
    val word: String? = ""
)

class ApplicationStore(
    initialState: ApplicationState = ApplicationState(),
    middlewares: List<Middleware<ApplicationState>> = emptyList(),
    reducers: List<Reducer<ApplicationState>> = listOf(ApplicationReducer)
) : AbstractStore<ApplicationState>(initialState, middlewares, reducers)

sealed class ApplicationAction : Action {

    /**
     * Dispatch this to set number value.
     */
    data class SetNumber(
        val newNumber: Int?
    ) : ApplicationAction()

    /**
     * Dispatch this to set flag value.
     */
    data class SetFlag(
        val newFlag: Boolean?
    ) : ApplicationAction()

    /**
     * Dispatch this to set word value.
     */
    data class SetWord(
        val newWord: String?
    ) : ApplicationAction()
}

object ApplicationReducer : Reducer<ApplicationState> {
    override fun reduce(action: Action, state: ApplicationState): ApplicationState {
        return when (action) {
            is ApplicationAction.SetNumber ->
                state.copy(number = action.newNumber)

            is ApplicationAction.SetFlag ->
                state.copy(flag = action.newFlag)

            is ApplicationAction.SetWord ->
                state.copy(word = action.newWord)

            else ->
                state
        }
    }
}
