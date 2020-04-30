---
title: "Minimalist Dagger for multi-modules"
subtitle: "How to make Dagger as simple as Koin, even with multiple modules."
layout: post
tags: dagger
author: Grégory Lureau
background: "/pictures/samantha-gades-BlIhVfXbi9s-unsplash.jpg"
---

This article is the following of the [Minimalist Dagger](/2020/04/12/Minimalist-Dagger) for monolithic app, I'll start from there.

---

In the previous article, we've defined the minimum set of tools to use DI advantages with the minimum setup.
This setup was enough for a monolithic application, but at some points, some of your apps will need to modularize.

This approach aims to keep the simplicity of the 1st article in a multi-module app, while limiting the build cost due to code generation.

There are tons of great modularization articles, so to resume, a module is a unit of code functionnally and physically distinct.
They have nothing in common with a Dagger **@Module**, you can have multiple **@Module**s or none in your "compilation" module. (All references to a module in this article refers to the compilation module.)
Generally each one of these units will have distinct dependencies (to libraries or/and to other modules), so our first need is to describe 
what we need to have to work. 
Inside the module, you'll also need to inject some fields like some controller in your views. 
Eventually if this module is also used by some other modules, you could have to expose that... But it's tedious and this article aims to provide a concrete solution to implement DI in your app, not in an open-source library, so let's keep it super simple.

# The pattern

Let's say we have a **Login** module, we'll add 1 file in the module to define these lines:

1 - The InjectorProvider is just an interface to be implemented by the Application itself, to expose the injector.

	interface LoginInjectorProvider { // Will be implemented by the Application itself
		fun loginInjector(): LoginInjector
	}

2 - The injector interface lists all the classes where you want to inject fields (generally your activities and fragments).

	interface LoginInjector {
		fun inject(loginFragment: LoginFragment)
	}

3 - Potentially some extension functions to make the injection easier. For example:

	fun Activity.loginInjector() = (this.application as LoginInjectorProvider).loginInjector()
	fun Fragment.loginInjector() = (this.activity?.application as LoginInjectorProvider?)?.loginInjector()
		?: error("Cannot inject without a proper reference to the application")
	fun View.loginInjector() = (this.context.applicationContext as LoginInjectorProvider).loginInjector()
	fun Service.loginInjector() = (this.applicationContext as LoginInjectorProvider).loginInjector()

You don't need to define them all, just pick what you want. As you can see, the idea here is to get the application context, cast it as our InjectorProvider, and get the injector from there. The cast is not safe, so if you've just added a new module and forgot to setup the Application, your app will crash at runtime with a ClassCastException. Thanksfully we're not creating one module by day, so this should be fine.

So this is working because our app now implement the InjectorProvider:

    class MyApplication: Application(), LoginInjectorProvider, FooInjectorProvider, BarInjectorProvider {
	    val component = DaggerAppComponent.create()
	    override fun loginInjector() = component
	    override fun fooInjector() = component
	    override fun barInjector() = component
	}

This way you can re-use any modules in any application, if the application implements the given InjectorProvider. 
As you can see, the Application file will increase from a couple of lines for each module. 
That's not elegant, but as it's pure kotlin interface implementation, if your setup is incomplete it'll not compile.

Last piece of this pattern, you need to update your AppComponent to give the ability to provide this new module:

	@Singleton
	@Component
	interface AppComponent: LoginInjector, FooInjector, BarInjector {
	}

Here too, the main component will grow a bit for each new modules. If you've hundreds of them, you could totally group them by meta features for example, it's pure interface Kotlin, you can be creative.


# Injecting

No changes when you want to inject via constructor.

	class Car @Inject constructor(val steeringWheel: SteeringWheel) {
	}

But if for example you want to inject fields in a Fragment from the Login module, you'll have to pick the module injector like this:

	class LoginFragment : Fragment(R.layout.fragment_login) {
	    @Inject
	    protected lateinit var viewModel: LoginViewModel
	 
        override fun onCreate(savedInstanceState: Bundle?) {
	        loginInjector().inject(this)
	        // ...
	    }
	    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
	        super.onViewCreated(view, savedInstanceState)
	        // Use viewModel to setup your view
	    }
	}

# Build time

In most of your modules you don't need to have any Dagger **@Module**, nor **@Component**.
The big advantage with this approach is that you don't have to use kapt on these modules!

Actually the code generation happens only at the app level, when building the AppComponent, where it's always required with Dagger 2 anyway.
There, due to our usage of interface inheritance, the AppComponent is actually declaring the inject methods for all the modules.

Our code:

	// In Login module
	interface LoginInjector {
		fun inject(loginFragment: LoginFragment)
	}
	
	// In Foo module
	interface FooInjector { 
		fun inject(fooFragment: FooFragment)
		fun inject(fooActivity: FooActivity)
	}
	
	// In the app module
	@Singleton
	@Component
	interface AppComponent: LoginInjector, FooInjector {
	}

Will produce an interface similar to this manual implementation:

	@Singleton
	@Component
	interface AppComponent: LoginInjector, FooInjector {
		fun inject(loginFragment: LoginFragment)
		fun inject(fooFragment: FooFragment)
		fun inject(fooActivity: FooActivity)
	}

Dagger2 see this inherited method and will generate the code for them.

So you can keep your code & dependencies grouped in each well-defined module, and it will result in a single component for all your application.


## Pros & Cons

Pros&Cons are similar to the [mono-module implementation](/2020/04/12/Minimalist-Dagger), but some differences:

#### Pros:
- Build time avoided on the Dagger-free submodules (good for unit-tests especially if you don't use Dagger in them, like me).
- Interface inheritance is way easier to understand than Dagger subcomponents.
- Dagger-free modules, this is exactly what Dependency Injection should look like\*.
- Dagger code is in less files, so changing the DI library is way more affordable than when using extensively Dagger2 subcomponents.

#### Cons:
- build time still paid when building the app (unless you want to try Dagger2 reflect, you have to pay code generation price anyway)

\* Imagine a library based on Dagger that requires you to provide some stuff through Dagger mechanism, or ask you to add a **@Modules** in your main component.
It's a main architectural problem because now you cannot change your DI library when you want to, and you coupled a library to another one (Dagger).
Fortunately all well-defined libraries avoid that (often by using static entry point or manual instanciation).

With this approach your modules can be Dagger-free, while still using Dagger 2 to build your dependency graph.

## FAQ

Post a comment and I'll try to provide a proper response in this article.

