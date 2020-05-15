---
title: "Should I use Jetpack ViewModel?"
subtitle: "All new shiny libraries are not good for all projects, it has to responds to a real problem first. Let's see if you should pick Jetpack ViewModel."
layout: post
tags: jetpack mvvm viewmodel
author: Grégory Lureau
background: "/pictures/barn-images-t5YUoHW6zRo-unsplash.jpg"
---

**Adding a new library to a production project should always be done with extra care.**

Jetpack introduced some months ago a [ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) library with a class of the same name. This new class "**store and manage UI-related data in a lifecycle conscious way**". It's not really limiting to the MVVM pattern actually and can be inherited by your presenters, controllers,...

In this post, I presume you know the basics of ViewModel (if you don't, there are many good resources out there) and we'll try to go further and understand the implications of adding this library into a project.



# Lifecycles

One of the sexiest part of ViewModel, from my point of view, was the idea of 'a simpler lifecycle'. Android lifecycles are quite complex, so this is quite a big deal for simplifying our code and spend more time on feature than on fixing lifecycle-related issues.

Let's look at this super simple lifecycle!

![](/pictures/Jetpack-ViewModel-activitylifecycle.png)

This look sexy! All is handled in the body of the ViewModel, I don't even need to remember lifecycle methods anymore...

And it handles re-creation! And I even have a method to clean stuff if needed!

Unfortunately you'll always need to have lifecycles to know if you're drawn, if you're re-resumed, or re-started to play an animation at the right moment...

**But first let's take a bigger picture with this version:**

![](/pictures/Jetpack-ViewModel-Lifecycles.png)

(picture from [The Android Lifecycle cheat sheet](https://medium.com/androiddevelopers/the-android-lifecycle-cheat-sheet-part-iv-49946659b094) by Jose Alcérreca)

Let's point out some details from this picture:
- ViewModel is not present when the `Activity` is created
- ViewModel is present when the `Activity` is re-created with `onCreate(Bundle)`
- ViewModel is always present in `onDestroy` of the `Activity` (not very symetrical to the onCreate)

And for the Fragment version

- ViewModel is not present when the `Fragment` is attached or created.
- ViewModel is present when it's re-attached or re-created (so in `onAttach` you cannot know if it exists or not)
- ViewModel may be present in `onDestroy` of the `Fragment` (you don't really know)


The Kotlin extensions to retrieve a ViewModel use the lazy mechanism, so you think you deal with a non-null variable in your code but actually you deal with a "it depends" variable if it's used inside the `onAttach` or `onCreate` methods. And the "it depends" is directly translatable by *"maybe it will crash your app"* since it's generally used from the main thread.

Adds to that Fragment can be retained (`onDestroy`/`onCreate` no more called when device rotate), that there is unfrequent lifecycle methods like `Activity#onRestart`, `Activity#onNewIntent`, ... or that flags on the Intent actually change the Activity method calls. [Lifecycles are complex.](https://github.com/JoseAlcerreca/android-lifecycles/tree/a5dfd030a70989ad2496965f182e5fa296e6221a)

This is not really simplifying the lifecycles but adding a third one to the mix. New developers will still need to understand the `Activity`/`Fragment` lifecycles + the fact they can't use their ViewModel in all lifecycle methods.

That's said, if you limit your code in Activity / Fragment to the `onCreateView` / `onStart` / `onResume` and the symmetrical `onPause` / `onStop` / `onDestroyView`, you should be safe.

# Dependency injection

One of the big issue with Android and DI is the fact that the construction of Activities and Fragments are done by the system. As such, it's impossible to inject our dependencies directly in the constructor, and we have to rely on field injection:
- requires reflection (little impact on performance),
- requires internal/public visibility (bad encapsulation),
- coupling with the DI library,
- hide the dependencies, you can create a Fragment without knowing about its dependencies, or add yet another injected field without thinking "Am I not creating a class with too many dependencies?" (bad architecture),
- nullability/immutability issues (cognitive load -> adding `lazy { }` to class members to defer the work -> degrading performances due to additional useless inner classes)

If after that you've still some doubts, google "field injection".


Example of code where you'll experiment a crash (NullPointerException) because using field injection:

    class MyActivity: Activity() {
        @Inject lateinit var myService: Service

        private val url = myService.getUrl() // throws NullPointerException

        fun onCreate() {
            // Injecting your dependencies
        }
    }

Well, it's almost the same story for Jetpack ViewModels **by default**. ViewModels are created by a Factory, that is by default provided by Android, so you can start immediately, and get system injections like the application `Context` or the `SavedStateHandle`. You can still use field injection but you'll have the previous issues...

**Thanksfully you can plug your Dagger dependencies to the ViewModelProvider! Right?**

So if you use Dagger for example, you'll have to do some "black magic" to plug your dependencies in the ViewModel. Stuff like handling maps of `AbstractViewModelFactory` factories. Yep, factories of factories. **Like if DI was already so easy that we feel the urge to add yet an additional layer of complexity.**

A simple example of black magic from the [android blueprints](https://github.com/android/architecture-samples/blob/dagger-android/app/src/main/java/com/example/android/architecture/blueprints/todoapp/di/ViewModelFactory.kt) itself. Indeed if you take some time you'll understand all the implications of this code, but as it's not provided by the library, you'll have to maintain it at some point... 


One of the benefit of the [Minimalist Dagger](/2020/04/27/Minimalist-Dagger/) (and the [multi-modules article](/2020/04/28/Minimalist-Dagger-MultiModules/)) approach I presented before was that most of the modules don't use kapt while using Dagger 2, and don't even need to have a `@Module`. So you're reducing a lot the coupling with the DI library. If you "upgrade" to Jetpack ViewModel and use the code provided in the architecture blueprint, you now need a bind method for each ViewModel, like in the [blueprint example](https://github.com/android/architecture-samples/blob/dagger-android/app/src/main/java/com/example/android/architecture/blueprints/todoapp/di/StatisticsModule.kt) :

    @Module
    abstract class StatisticsModule {
        @Binds
        @IntoMap
        @ViewModelKey(StatisticsViewModel::class)
        abstract fun bindViewModel(viewmodel: StatisticsViewModel): ViewModel
    }

And now all your modules knows about Dagger (`@Module`, `@Binds`, `@IntoMap` + your custom annotation) and you run kapt in each one of them. For the build time I'm not sure, for the dependency to Dagger I presume it's "ok-ish" (at best) as Android will invest more on this lib, but for all the developers thinking Dagger is too much verbose...

Did I tell you this black magic code was actually the simplest case? 

What if you want to use a SavedStateHandle or inject the App context... or both? This approach is not enough, so you have 2 options: make more black magic DI code, or just make a [big switch cases with all the constructors of all your ViewModels](https://github.com/android/architecture-samples/blob/cfb5ac6ea6a5c888b171d88d7ea4287a33af5cb9/app/src/main/java/com/example/android/architecture/blueprints/todoapp/ViewModelFactory.kt).

    @Suppress("UNCHECKED_CAST")
    class ViewModelFactory constructor(
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null,
        private val tasksRepository: TasksRepository
        /* Plug all your module dependencies here */
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

        override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
        ) = with(modelClass) {
            when {
                isAssignableFrom(StatisticsViewModel::class.java) -> StatisticsViewModel(tasksRepository)
                isAssignableFrom(TaskDetailViewModel::class.java) -> TaskDetailViewModel(tasksRepository)
                isAssignableFrom(AddEditTaskViewModel::class.java) -> AddEditTaskViewModel(tasksRepository)
                isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(tasksRepository, handle)
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T
    }

Indeed the blueprints is not super scalable (maybe it's not even intended to be scalable), but it means than before Jetpack, to add a ViewModel, you created a new class, and referenced the class in the Fragment, so modifying 2 files. Now you also need to create a dedicated module, and update your big switch case.

What if you want to add another dependency to your ViewModel? Again need to edit the "DI file", add yet another field in the constructor of your ViewModelFactory... Does it go over your code-quality metrics now?

Also let's say you've a DefaultViewModel that can be instantiated, but also 2 other subclasses with slightly different behaviors. Now the big switch case needs to be ordered to avoid a bad match, and documented because it's not that obvious that order matters here...

Yes there are other approaches to the problem. But no default approach, or no viewmodel-dagger2 lib that does the work for you yet (this `when` is pure boilerplate and could be automated), so you'll have to find a good approach for you before anything else. I'm confident Android engineers will deliver a solution at some point, but when? And with what limitations?

Ok... let's say we have copy-pasted some files from github or StackOverflow to make Dagger happy, now we're good on DI right?

## Analysis at compile-time

Well actually, before Jetpack ViewModel, Dagger2 was checking all dependencies and was ensuring everything was provided. It means than if it builds, you don't have dependency issue. Is it still true?

No. As you can see in the `when`, the `ViewModelProvider.Factory` signature is based on a Class:

    <T extends ViewModel> T create(@NonNull Class<T> modelClass);

So you don't have the choice than **crash at runtime** when the class given in parameters is not mapped in your code. Also, as we've seen in the Lifecycles part, you cannot create the ViewModel out of a specific lifecycle without crashing, so clearly the dependency injection with ViewModel is no more checked at compile-time. To get back the analysis at compile-time, you can indeed add another annotation processor library, costing more build time...

## Communication between views

Using ViewModel allow to easily interact between Activity and Fragment. Let's say for example there is a ViewModel at the Activity level and a Fragment want to notify of some changes, dead simple (with fragment-ktx 1.1.0+):

    class MyViewModel {
        fun getViewState(): MyViewState { ... }
        fun updateStuff() { ... }
    }
    class MyActivity: Activity() {
        private val myViewModel by viewModels<MyViewModel>()
        override fun onResume() {
            updateView(myViewModel.getViewState())
        }
    }
    class MyFragment: Fragment() {
        private val myViewModel by activityViewModels<MyViewModel>()
        fun onClick() {
            myViewModel.updateStuff()
        }
    }

This is **really cool**, there will be only one instance of the MyViewModel in this case. Before you were limited to:
- inject in the Activity with your DI, then manually injects your fragments when they are attached. This leaks the dependencies of your Fragments in the Activity, so limiting reusability.
- create a scoped Component by Activity, adding some complexity in your DI.

Actually for Dagger users, that's like there were 2 new scopes "ActivityScope" / "FragmentScope" and a dynamic Component for each activity started, so you don't have to write them!

As a corollary, a custom view can use a ViewModel from its Activity or Fragment, but not from the view itself. You can still create a MyViewModel with Dagger and inject it in the view, but it will not use the ViewModel mechanism, so no `SavedStateHandle` or `onCleared()`, so MyViewModel doesn't need to extend ViewModel.

Same for a ViewModel that needs another ViewModel, there is no solution as far as I know.

Note that the scopes are hidden, so if a fragment uses `by viewModel<MyViewModel>()` and the activity uses `by viewModel<MyViewModel>()`, both will have a distinct MyViewModel instance. Fragment should have used  `by activityViewModel<MyViewModel>()` to share the same instance. This kind of setup error will probably occurs at some point.

## Communication between ViewModels

Now let's say there is an Activity with a "master" ViewModel, and 2 Fragments, each one having its own ViewModel. The master requires the validation of the 2 sub ViewModels. How can we plug that?

- We can declare the 2 Fragment's ViewModels on the (invisible) "Fragment scope", and the Activity listen onAttach -> Nop, the master ViewModel needs to know about the 2 others but the Activity itself doesn't need to.
- We can declare the 3 ViewModels as scoped to the Activity, but then the Activity needs to plug them together.
- We can declare the 2 Fragment's ViewModels on the (invisible) "Fragment scope", and depends on the master ViewModel like this:
<p></p>

    class FragmentA : Fragment() {
        val masterViewModel by activityViewModels<MasterViewModel>()
        val viewModel by viewModels<AViewModel>()
        fun onResume() {
            masterViewModel.addSlave(viewModel)
            // or
            viewModel.onChange { masterViewModel.notifyAChanged() }
            // or anything doing the glue between both ViewModels
        }
    }

Whatever the solution that fits your need, there is still a bit of glue. I'd love a solution to ask a ViewModel (if present) from another one, so that I don't need to change the Activity/Fragment classes anymore. **Please share in comments if you know a solution!**

By the way, Activity has generally a longer scope than Fragment, so passing the ViewModel of the Fragment to an Activity ViewModel will probably lead to a memory leak.

# Provides data to ViewModel

A major benefits with `SavedStateHandle` is the possibility to get the Activity's intent `extras`, or the Fragment's `arguments` from the ViewModel. When it's pluggued (black magic DI), you'll have a very pleasant experience:

    class MyViewModel(savedStateHandle: SavedStateHandle): ViewModel() {
        companion object {
            private const val ARG_KEY = "ARG_KEY"
            fun bundle(userId: String) = bundleOf(ARG_KEY to userId)
        }
        private val userId: String? = savedStateHandle.get<String>(ARG_KEY)
        init { println(userId) }
    }

    class MyFragment: Fragment() {
        companion object {
            fun newInstance(userId: String) = MyFragment()
                .apply { arguments = MyViewModel.bundle(userId)}
        }
    }


A big win here, you can add parameters to the bundle without modifying your view anymore.

# Conclusion

This is indeed completely up to you!

But just to sum up my feelings about it:

| **Topic** | **without ViewModel** | **Jetpack ViewModel** |
| Lifecycle complexity | 😔 | 😔 |
| DI complexity | 👌 | 😔 |
| Checks DI at compile-time | 😍 | 😔 |
| Sharing ViewModel | 😔 | 😍 |
| Communication between ViewModel | 😐 | 😐 |
| Handle saved states | 😔 | 😍 |
| Passing args to ViewModel | 😐 | 😍 |

There are serious drawbacks to go with Jetpack ViewModel, but it's also helping to clean and reduce your glue code. In my main project, we've chosen to go further with ViewModel, but first we worked on some approaches to mitigate the drawbacks. Stay tuned for the next articles on that matter.
