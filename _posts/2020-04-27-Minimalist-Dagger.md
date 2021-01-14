---
title: "Minimalist Dagger"
subtitle: "How to make Dagger as simple as Koin"
layout: post
tags: dagger
author: Grégory Lureau
background: "/pictures/samantha-gades-BlIhVfXbi9s-unsplash.jpg"
---

Dagger 2 is often considered as too complex for reasons, this article will try to propose a really simple approach limiting complexity and easing the learning curve for newcomers.

I purposefully skip a lot of details, because I consider most of the details have a bad complexity/gain ratio, but we'll go further on this point on next articles.


## Annotations

### JSR-330

Developed with Java, this set of annotations define how to declare DI and contains:

**@Inject** : Applied to a constructor or a field, it indicates this dependency will be provided eventually.

**@Singleton** : Applied to a class, when you want to have only one instance of it.

This de-facto library only declares annotations, it doesn't need apt/kapt so it doesn't have any build time cost and is very lightweight. You can use these annotations with other libraries than Dagger, so having them in your code doesn't mean you are coupled to Dagger. So you'll be able to change to another JSR-330 compatible DI library (eg. Kodein) when you want to, without modifying all your classes.

### Dagger2

For the basic needs we'll only use:

**@Component** : to define a component that will contain every dependencies

**@Module** + **@Provides** : to define how to create instances that don't belong to our code. A classic example is for creating an okhttp instance.

## Implementation strategy

Scopes and multi components are complex to deal with, and can lead to subtle errors. We want to be efficient in what we develop, focus on business logic, not on this kind of errors, so no need to have that complex memory management. That's it.

If you need an instance of an object and you don't care about sharing, just use @Inject on the class constructor you want to inject and where you need the dependency.

    class SteeringWheel @Inject constructor() {
        // Use @Inject on an empty constructor will define 
        // to your DI library how to create the class.
    }

    class Car @Inject constructor(val steeringWheel: SteeringWheel) {
        // When creating a car, your DI library will create a new SteeringWheel.
    }

If you need to keep an object in memory for the entire application's lifetime, you just have to add @Singleton on the shared class:

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

## Implementation

First you need to define a Component for the application, to access your dependencies.

    @Singleton
    @Component
    interface AppComponent {
        // List all the classes where you want to inject fields (not required when injecting via constructor)
        // Can also add accessor to singleton objects if required.
    }

And a way to access the component from everywhere when injecting fields:

    class MyApplication : Application() { // Update your manifest accordingly if you created this new Application class
        val component = DaggerAppComponent.create()
    }

    val Fragment.injector: AppComponent
        get() = (requireActivity().application as MyApplication).component

You're good to go!

Want to try on a showcase project? [glureau/MinimalistDagger](https://github.com/glureau/MinimalistDagger/)


### An example 

Let's define a Singleton:

    @Singleton
    class NotificationManager @Inject constructor() {
        val count: Int = 0
    }

An unscoped ViewModel:

    class DashboardViewModel @Inject constructor() {
        val text: String = "Dashboard"
    }

Now when using a fragment, you can do :

    class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
        
        @Inject
        protected lateinit var notification: NotificationManager
        @Inject
        protected lateinit var viewModel: DashboardViewModel
        
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            injector.inject(this)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val textView: TextView = view.findViewById(R.id.text_dashboard)
            textView.text = viewModel.text + " (notif=" + notification.count + ")"
        }
    }

## Things to keep in mind

Singletons are not Java static classes nor Kotlin **object**s. They are unique only in our Dagger component, so using @Singleton and using a static variable are fundamentally different.

Singletons are lazily created, it means you are delaying their initialization until the first time they are needed. If you define a singleton with a behaviour in the constructor but no one refers to it, this class will never be created, and the code in construtor will never run. 
A simple workaround can be to add a line in your application to trigger the lazy resolution. 

    class MyApplication : Application() {
        val component = DaggerAppComponent.create()
        init {
            component.getMySingleton()
        }
    }

Once instantiated, a Singleton will never be released, unless the app is terminated (crash, process killed manually or by the OS).

If the process crash (low memory -> Android decides to kill your process), the data in Singletons (or even Java static classes/kotlin objects) are lost, BUT the backstack (Activities/Fragments) are saved automatically by the OS. So if you do nothing, when the app restore a screen, it will start with a new Singleton. As a consequence, you should not rely on the fact that you came from another screen that has passed the data to a singleton to retrieve it.
In this case, a few options:
- Store the states you want to reload (onSaveInstanceState / SharedPreferences / Database / SavedStateHandle / ...)
- Detect the restoration and reload your application to the default screen (startActivity with a flag to clear all other activies, and restart from a clean state).
The latter is great for little teams or projects that don't want to spend too much time on data migration and tests. The first option will provide a better user experience in those cases.

Using **@Singleton** or using nothing means there are 2 kind of scope, the App scope (**@Singleton**), and the unscoped. So even if they are not custom scope, it's important to understand the difference.

## Pros & Cons

Pros:
- Easy to understand for newcomers, no time spent trying to understand how the Dagger class binding is working or where I should write my modules and sub-components.
- No boilerplate, so it's super easy to maintain (actually it's free on our project).
- A big improvement for the team moral, no more time spent trying to understand generated code.

Cons:
- RAM usage: you'll keep Singleton annotated classes probably longer than what is strictly required. 
As a team of 7 Android developers at Betclic, we are working on a 150k LoC sport betting application installed on 400k+ devices displaying thousands of matches, animating betting odds updates in realtime. We have a 99.9% crash free rate, and the 0.1% of the crashes are not related to memory issues, so I don't think it matters that much.
- No scoping: you'll have to clean the data in your Singletons when it's not used anymore, instead of just dropping a sub-component. (example in FAQ)
- Singleton or manual injection when you need to share some data or ViewModels: if using Singleton, they could be poorly implemented and hold some data related to a specific page. Good practices need to be in place to avoid that.
<br/>
<br/>
<br/>

-----

## FAQ

Post a comment and I'll try to provide a proper response in this article.

### I want to keep scoped data, like user related information when logged in, and clean that when the user is logged off.

Doing that with DI is complex, but you can easily have a @Singleton class that will handle that, for example :

    @Singleton class UserManager @Inject() {
        var user: User? = null
        fun login(u: User) { user = u }
        fun logout() { user = null }
    }

The main implication is that you need to know when you do or don't use it anymore.

### I have multiple instances of the same class that I want to inject.

It can happen in some projects, like for example a couple of okhttp client instances with distinct setup.

If you encounter this specific case, you will have to define a Module to provide instances, and on the provide method add a simple **@Named("some_name")**. Now when you need to inject one specific instance, you'll simply add the @Named annotation on the field:

    class MyClass @Inject(
        @Named("public") private val httpClient: OkHttpClient
    )

### Can I really keep this minimalism in a multi-module architecture?

[Sure you can!](/2020/04/28/Minimalist-Dagger-MultiModules/)

<br/>

# Showcase project

[glureau/MinimalistDagger](https://github.com/glureau/MinimalistDagger/)

<br/>
Thanks to Matthieu Coisne for correcting my bad english.
<br/>
<br/>
<br/>

-----

## Related articles & videos

Discovered this nice video while writing this article, great content!
<div class="youtube-container">
    <iframe width="560" height="315" src="https://www.youtube.com/embed/9fn5s8_CYJI" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
</div>