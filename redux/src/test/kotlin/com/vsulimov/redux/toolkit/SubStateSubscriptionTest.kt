package com.vsulimov.redux.toolkit

import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
internal class SubStateSubscriptionTest {

    private lateinit var store: ApplicationStore

    private var numberStateChangeCallsCount = 0
    private var flagStateChangeCallsCount = 0
    private var wordStateChangeCallsCount = 0

    private val numberSubscription = SubStateSubscription<ApplicationState, Int>(
        transform = { it.number },
        onStateChange = { _: Int, _: Boolean -> numberStateChangeCallsCount++ }
    )
    private val flagSubscription = SubStateSubscription<ApplicationState, Boolean>(
        transform = { it.flag },
        onStateChange = { _: Boolean, _: Boolean -> flagStateChangeCallsCount++ }
    )
    private val wordSubscription = SubStateSubscription<ApplicationState, String>(
        transform = { it.word },
        onStateChange = { _: String, _: Boolean -> wordStateChangeCallsCount++ }
    )

    @Before
    fun setUp() {
        store = ApplicationStore()
        numberStateChangeCallsCount = 0
        flagStateChangeCallsCount = 0
        wordStateChangeCallsCount = 0
    }

    @Test
    fun `handleStateChange should be called when new subscriber is added to the store`() {
        store.subscribe(numberSubscription)
        store.subscribe(flagSubscription)
        store.subscribe(wordSubscription)

        assert(numberStateChangeCallsCount == 1)
        assert(flagStateChangeCallsCount == 1)
        assert(wordStateChangeCallsCount == 1)
    }

    @Test
    fun `handleStateChange should be called for existing subscribers when state changed`() {
        store.subscribe(numberSubscription)
        store.subscribe(flagSubscription)
        store.subscribe(wordSubscription)

        store.dispatch(ApplicationAction.SetNumber(newNumber = 1))
        store.dispatch(ApplicationAction.SetFlag(newFlag = true))
        store.dispatch(ApplicationAction.SetWord(newWord = "Word"))

        assert(numberStateChangeCallsCount == 2)
        assert(flagStateChangeCallsCount == 2)
        assert(wordStateChangeCallsCount == 2)
    }

    @Test
    fun `handleStateChange should not be called for unsubscribed state change listeners`() {
        store.subscribe(numberSubscription)
        store.subscribe(flagSubscription)
        store.subscribe(wordSubscription)

        store.unsubscribe(numberSubscription)
        store.unsubscribe(flagSubscription)
        store.unsubscribe(wordSubscription)

        store.dispatch(ApplicationAction.SetNumber(newNumber = 1))
        store.dispatch(ApplicationAction.SetFlag(newFlag = true))
        store.dispatch(ApplicationAction.SetWord(newWord = "Word"))

        assert(numberStateChangeCallsCount == 1)
        assert(flagStateChangeCallsCount == 1)
        assert(wordStateChangeCallsCount == 1)
    }

    @Test
    fun `handleStateChange should not be called if state is the same after reduction`() {
        store.subscribe(numberSubscription)
        store.subscribe(flagSubscription)
        store.subscribe(wordSubscription)

        store.dispatch(ApplicationAction.SetNumber(newNumber = 0))
        store.dispatch(ApplicationAction.SetFlag(newFlag = false))
        store.dispatch(ApplicationAction.SetWord(newWord = ""))

        assert(numberStateChangeCallsCount == 1)
        assert(flagStateChangeCallsCount == 1)
        assert(wordStateChangeCallsCount == 1)
    }

    @Test
    fun `handleStateChange should not be called if transformed state is null`() {
        store.subscribe(numberSubscription)
        store.subscribe(flagSubscription)
        store.subscribe(wordSubscription)

        store.dispatch(ApplicationAction.SetNumber(newNumber = null))
        store.dispatch(ApplicationAction.SetFlag(newFlag = null))
        store.dispatch(ApplicationAction.SetWord(newWord = null))

        assert(store.getState().number == null)
        assert(store.getState().flag == null)
        assert(store.getState().word == null)

        assert(numberStateChangeCallsCount == 1)
        assert(flagStateChangeCallsCount == 1)
        assert(wordStateChangeCallsCount == 1)
    }
}
