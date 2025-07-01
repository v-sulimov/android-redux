package com.vsulimov.redux.middleware

import com.vsulimov.redux.Action
import com.vsulimov.redux.TypedMiddleware
import com.vsulimov.redux.action.TestAction
import com.vsulimov.redux.state.TestState
import kotlinx.coroutines.CoroutineScope

/**
 * A typed middleware that doubles the value in [TestAction.Add] actions.
 */
class DoubleAddMiddleware : TypedMiddleware<TestAction.Add, TestState>(TestAction.Add::class.java) {
    override fun invokeTyped(
        action: TestAction.Add,
        state: TestState,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    ) {
        next(TestAction.Add(action.value * 2))
    }
}
