# Redux (Kotlin)

A predictable state container for Android apps.

# What Redux is and how it works?
You can read more about Redux on the [official website](https://redux.js.org/).
<br>
This library is just Kotlin implementation of Redux architecture with Android compatibility in mind.

## Redux Components
#### Action
You can declare your actions as a sealed classes. Make sure you don't forget
to implement Action interface.
```kotlin
sealed class CounterAction : Action {

    object Increment : CounterAction()

    object Reset : CounterAction()
}
```

#### Middleware
Middleware represented as a class that implements the Middleware interface (so you can use dependency injection into your middlewares to perform the necessary job).

You can return new action instead of incoming by calling ```next(action, state)```.
```kotlin
class ActivityLifecycleMiddleware : Middleware<ApplicationState> {
    override fun handleAction(
        action: Action,
        state: ApplicationState,
        next: Next<ApplicationState>
    ): Action {
        val newAction = when (action) {
            is ActivityLifecycleAction.OnResume ->
                YourNewAction()
            else ->
                action
        }
        return next(newAction, state)
    }
}
```

#### Reducer
Reducers also represented as a class that implements the Reducer interface (for testability reasons).
Keep in mind that reducer should act as a pure function. All your business logic should be in the middleware.
```kotlin
object CounterReducer : Reducer<Int> {
    override fun reduce(action: Action, state: Int): Int {
        return when (action) {
            CounterAction.Increment ->
                state.inc()

            CounterAction.Decrement ->
                state.dec()

            CounterAction.Reset ->
                0

            else ->
                state
        }
    }
}
```

#### State
The application state are typically represented as an immutable data class.
It allows to easily performs copy on write operations on that state in the reducers.

#### Store
Base store implementation available in AbstractStore class.
Simply extend it, provide initial state, middlewares, reducers and you're ready to rock!
```kotlin
class ApplicationStore(
    initialState: ApplicationState = ApplicationState(),
    middlewares: List<Middleware<ApplicationState>> = listOf(ApplicationMiddleware()),
    reducers: List<Reducer<ApplicationState>> = listOf(ApplicationReducer)
) : AbstractStore<ApplicationState>(initialState, middlewares, reducers)
```

## Toolkit
Toolkit package contains utilities to simplify common use cases.
#### Subscribe to the part of the entire state and filter out subsequent repetitions.
```kotlin
data class ApplicationState(
    val number: Int = 0,
    val flag: Boolean = true,
    val word: String = "Hello, World!"
)

store.subscribe(SubStateSubscription(
    transform = { it.number },
    onStateChange = { number: Int, isInitialState: Boolean -> // Do something with value }
))
store.subscribe(SubStateSubscription(
    transform = { it.flag },
    onStateChange = { flag: Boolean, isInitialState: Boolean -> // Do something with value }
))
store.subscribe(SubStateSubscription(
    transform = { it.word },
    onStateChange = { word: String, isInitialState: Boolean -> // Do something with value }
))
```

## Android compatibility
This library contains Redux-friendly version of AppCompatActivity, which
dispatch ActivityLifecycleAction whenever the activity lifecycle is changed.
It's good idea to extend that class in order to use Redux architecture in your application.

## Tests
All core components are covered with tests to prevent
regressions during future development.
Also, if you ever want to write your implementation of the
Store or AppCompatActivity it's a good idea to make sure that
these tests also pass for your implementation.

## Android Studio integration
This repository contains the Live Templates configuration file for faster code writing
for Store, Actions, Middlewares, and Reducers.
Copy the ./android-studio/templates/redux.xml file to the templates directory
of your IDE configuration (the exact path depends on your OS).
After that, you can use the store, action, middleware, and reducer abbreviations to
insert the appropriate live template.

## License
<pre>
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
