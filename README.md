# Android Redux

A lightweight, type-safe Redux implementation for Android applications, designed to manage application state in a predictable and scalable way using Kotlin's powerful type system and concurrency features.

## Table of Contents
- [Installation](#installation)
- [Usage](#usage)
- [License](#license)
- [Support](#support)

## Installation

### Prerequisites
- Android SDK with a minimum API level of 24 (Android 7.0 Nougat)
- Gradle build system
- Kotlin for development

### Steps
1. Add the Redux for Android repository to your `settings.gradle.kts` (or `settings.gradle` for Groovy):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "repository"
            url = uri("https://maven.vsulimov.com/releases")
        }
    }
}
```

2. Include the Redux for Android dependency in your `build.gradle.kts` (or `build.gradle` for Groovy):

```kotlin
dependencies {
    implementation("com.vsulimov:redux:1.0.4")
}
```

3. Sync your project with Gradle to download the library.

### Platform Notes
- For Groovy-based projects, replace `.kts` with `.gradle` and use Groovy syntax.
- Ensure your project uses AndroidX libraries for compatibility.

## Usage

### Defining the State
Define your application state as a Kotlin data class:

```kotlin
data class ApplicationState(val counter: Int = 0)
```

### Defining Actions
Actions represent intents to change the state and must implement the `Action` interface. Use sealed classes for type safety:

```kotlin
sealed class CounterAction : Action {
    object Increment : CounterAction()
    object Decrement : CounterAction()
}
```

### Defining Reducers
Reducers are pure functions that compute a new state based on the current state and an action. Use the `Reducer` functional interface:

```kotlin
val counterReducer = Reducer<ApplicationState> { action, state ->
    when (action) {
        is CounterAction.Increment -> state.copy(counter = state.counter + 1)
        is CounterAction.Decrement -> state.copy(counter = state.counter - 1)
        else -> state
    }
}
```

For type-safe handling of specific actions, use `TypedReducer`:

```kotlin
class CounterReducer : TypedReducer<CounterAction, ApplicationState>(CounterAction::class.java) {
    override fun reduceTyped(action: CounterAction, state: ApplicationState): ApplicationState {
        return when (action) {
            is CounterAction.Increment -> state.copy(counter = state.counter + 1)
            is CounterAction.Decrement -> state.copy(counter = state.counter - 1)
        }
    }
}
```

### Defining Middlewares
Middlewares intercept actions before they reach reducers, enabling side effects or action transformations. Use the `Middleware` functional interface:

```kotlin
val loggingMiddleware = Middleware<ApplicationState> { action, state, next, dispatch, scope ->
    Log.d("Redux", "Action: $action, State: $state")
    next(action)
}
```

For asynchronous operations, leverage the provided `CoroutineScope`:

```kotlin
val asyncMiddleware = Middleware<ApplicationState> { action, state, next, dispatch, scope ->
    if (action is CounterAction.Increment) {
        scope.launch {
            delay(1000) // Simulate async work
            dispatch(CounterAction.Decrement) // Dispatch a new action
        }
    }
    next(action)
}
```

For type-safe middleware, use `TypedMiddleware`:

```kotlin
class IncrementMiddleware : TypedMiddleware<CounterAction.Increment, ApplicationState>(CounterAction.Increment::class.java) {
    override fun invokeTyped(action: CounterAction.Increment, state: ApplicationState, next: (Action) -> Unit, dispatch: (Action) -> Unit, scope: CoroutineScope) {
        Log.d("Redux", "Increment action intercepted")
        next(action)
    }
}
```

### Creating the Store
Initialize the store with an initial state and optionally customize thread checking:

```kotlin
val store = Store(initialState = ApplicationState(), isMainThread = { Looper.myLooper() == Looper.getMainLooper() })
store.addReducer(counterReducer)
store.addMiddleware(loggingMiddleware)
store.addMiddleware(asyncMiddleware)
```

### Dispatching Actions
Dispatch actions to update the state:

```kotlin
store.dispatch(CounterAction.Increment)
```

### Observing State Changes
Observe state updates using the `stateFlow` property in a lifecycle-aware scope:

```kotlin
lifecycleScope.launch {
    store.stateFlow.collect { state ->
        // Update UI with new state, e.g., textView.text = state.counter.toString()
    }
}
```

**Thread Safety Note**: The store ensures operations occur on the main thread by default. Customize this behavior with the `isMainThread` parameter if needed. Middlewares use a `CoroutineScope` tied to `Dispatchers.Main` for asynchronous tasks.

## License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Support
For issues, feature requests, or questions, please:
- Contact the developer at [v.sulimov.dev@imap.cc](mailto:v.sulimov.dev@imap.cc).
