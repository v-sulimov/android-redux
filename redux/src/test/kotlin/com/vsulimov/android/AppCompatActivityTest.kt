package com.vsulimov.android

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    application = TestApplication::class,
    sdk = [Build.VERSION_CODES.P]
)
class AppCompatActivityTest {

    @Before
    fun setUp() {
        getApplication().setDefaultStore()
    }

    @Test
    fun `onCreate onStart and onResume should be called once when activity is created`() {
        ActivityScenario.launch(TestActivity::class.java)
        getApplication().store.getState().apply {
            assert(onCreateActionsCount == 1)
            assert(onStartActionsCount == 1)
            assert(onResumeActionsCount == 1)
            assert(onPauseActionsCount == 0)
            assert(onStopActionsCount == 0)
            assert(onDestroyActionsCount == 0)
        }
    }

    @Test
    fun `onPause onStop and onDestroy should be called once when activity is destroyed`() {
        ActivityScenario.launch(TestActivity::class.java)
            .also { it.moveToState(Lifecycle.State.DESTROYED) }
        getApplication().store.getState().apply {
            assert(onCreateActionsCount == 1)
            assert(onStartActionsCount == 1)
            assert(onResumeActionsCount == 1)
            assert(onPauseActionsCount == 1)
            assert(onStopActionsCount == 1)
            assert(onDestroyActionsCount == 1)
        }
    }

    @Test
    fun `activity re-creation scenario`() {
        ActivityScenario.launch(TestActivity::class.java)
            .also { it.recreate() }
        getApplication().store.getState().apply {
            assert(onCreateActionsCount == 2)
            assert(onStartActionsCount == 2)
            assert(onResumeActionsCount == 2)
            assert(onPauseActionsCount == 1)
            assert(onStopActionsCount == 1)
            assert(onDestroyActionsCount == 1)
        }
    }

    private fun getApplication(): TestApplication =
        ApplicationProvider.getApplicationContext()
}
