package com.vsulimov.redux.data

import com.vsulimov.redux.ApplicationState

/**
 * Represents the state of the test application.
 *
 * @property count The current count value, defaults to 0.
 */
data class TestState(val count: Int = 0) : ApplicationState
