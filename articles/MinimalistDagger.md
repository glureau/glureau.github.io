# Minimalist Dagger

Dagger 2 is often considered as too much complex for reasons, this article will try to propose a really simple approach limiting complexity and learning curve for newcomers.

It purposefully skip a lot of details, because I consider most of the details have a bad complexity/gain ratio, but we'll go further on this point on next articles.

Let's starts with the annotations I need to know and how to implement. Then I'll define the pros and cons of the solution.


## Annotations

### JSR-330

Developed with Java, this set of annotations define how to declare DI and contains:

@Inject : Applied to a constructor or a field, it indicates this dependency will be provided sometimes

@Singleton : Applied to a class, when you want to have only one instance of it.

(@Named : When we need to deal with multiple instances of the same type, for example multiple okhttp clients. You may not need it.)

This de-facto library only declare annotations, it doesn't need apt/kapt so doesn't have any build time cost and is very lightweight. You can use these annotations with other libraries than Dagger, so having them in your code doesn't mean you are coupled to Dagger. So you'll be able to change to another JSR-330 compatible DI (eg. Kodein) when you want to, without modifying all your classes.

### Dagger2

For the basic needs we'll only use:

@Component : to define a component that will contains every dependencies

@Module : to define how to create instances that doesn't belong to our code. A classic example is for creating an ohttp instance.

## Implementation strategy

Scopes and multi components are complex to deal with, and can lead to subtle errors. We want to be efficient in what we develop, focus on business stuff, not on this kind of errors, so no need to have that complex memory management. That's it.

If you need an instance of an object and you don't care about sharing it, just use @Inject on the class constructor you want to inject and where you need the dependency.

class SteeringWheel @Inject constructor() {
	// Use @Inject on an empty constructor will define to your DI library how to create the class.
	// The other way to do it is to create a Module, but that's more verbose and coupling with Dagger, we don't want that.
}

class Car @Inject constructor(val steeringWheel: SteeringWheel) {
	// When creating a car, your DI library will create a new SteeringWheel.
}

If you need to keep something for all the application run, you just have to add @Singleton on the shared class:

@Singleton
class World @Inject constructor() {
	val createdCityCount = AtomicInt(0)
	// Only one instance will be created (more on that later).
}

class City @Inject constructor(val world: World) {
	// When creating multiple cities, all cities will be created with the same world instance.
	init {
		world.createdCityCount.increment()
	}
}

Finally you need to define a Component for the application, to access your dependencies.


## Things to keep in mind

Singletons are not Java static classes nor Kotlin **object**s. They are unique only in the components we define, so using @Singleton and using a static variable are fundamentally different.
Singletons are created lazily, that means they are instantiated when required only. If you define a singleton with a behaviour in the constructor but no one refers to it, this class will never be created, and the code in construtor will never run.
Once instantiated, a Singleton will never be released, unless the app is terminated (crash, process killed manually or by the OS).

If the process crash (low memory -> Android decides to kill your process), the data in Singletons (or even static classes/kotlin objects) are lost, BUT the backstack (Activities/Fragments) are saved automatically by the OS. So if you do nothing, when the app restore a screen, it will start with a new Singleton, so you should not rely on the fact that you came from another screen that has passed the data to a singleton to retrieve it.
In this case, a few options:
- Store the states you want to reload (onSaveInstanceState/SharedPreferences/Database/SavedStateHandle/...)
- Detect the restoration and reload your application to the default screen (startActivity with a flag to clear all other activies, and restart from a clean state).
The latter is great for little teams or projects that don't want to spend too much time on data migration and tests. The first option will provide a better user experience in such not-so-rare cases.

Using @Singleton or using nothing special means there is 2 kind of scope, the App scope (@Singleton), and the unscoped. So even if they are not custom scope, it's important to understand the difference.

## Pros & Cons

- RAM usage


## FAQ

Post a comment and I'll try to provide a proper response in this article.

### I want to keep scoped data, like user stuff when logged in, and clean that when user is logged off.

Doing that with DI is complex, but you can easily have a @Singleton class that will handle that, for example :

@Singleton class UserManager @Inject() {
	var user: User? = null
	fun login(u: User) { user = u }
    fun logout() { user = null }
}

The main implication is that you need to know when you use or don't use it anymore.

## Related articles & videos

https://www.youtube.com/watch?time_continue=28&v=9fn5s8_CYJI&feature=emb_logo