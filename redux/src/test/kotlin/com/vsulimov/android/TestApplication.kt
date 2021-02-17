package com.vsulimov.android

import android.app.Application

/**
 * Custom redux application class with store.
 */
class TestApplication : Application() {

    lateinit var store: ApplicationStore

    init {
        setDefaultStore()
    }

    fun setDefaultStore() {
        store = ApplicationStore(
            middlewares = emptyList(),
            reducers = listOf(ApplicationReducer)
        )
    }
}
