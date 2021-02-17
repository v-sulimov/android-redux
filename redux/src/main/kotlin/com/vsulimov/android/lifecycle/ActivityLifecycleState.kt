package com.vsulimov.android.lifecycle

import com.vsulimov.android.activity.AppCompatActivity

/**
 * Activity lifecycle state corresponds to the current state of Android activity.
 * You can use this in your application in conjunction with [AppCompatActivity]
 * and [ActivityLifecycleAction] that their produces.
 *
 * **See Also:**
 * [Understand the Activity Lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
 */
enum class ActivityLifecycleState {

    /**
     * Corresponds to [AppCompatActivity.onResume] lifecycle event and
     * [ActivityLifecycleAction.OnResume] action.
     */
    RESUMED,

    /**
     * Corresponds to [AppCompatActivity.onStop] lifecycle event and
     * [ActivityLifecycleAction.OnStop] action.
     */
    STOPPED,

    /**
     * Corresponds to [AppCompatActivity.onDestroy] lifecycle event and
     * [ActivityLifecycleAction.OnDestroy] action.
     */
    DESTROYED
}
