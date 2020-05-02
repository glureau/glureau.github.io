---
title: "RxJava2 subscribeOn: How to use it"
subtitle: "subscribeOn is an operator that can be easily used in the wrong place and lead to subtle bugs and even ANR. Some tips to avoid the pitfalls."
layout: post
background: "/pictures/Android-ImageSpan-Autosizing-background.png"
---


As we saw in the [quizz about subscribeOn](/2020/05/01/RxJava-Puzzle-SubscribeOn), using subscribeOn can produce unexpected and subtle effects. 

# subscribeOn pitfalls

    Observable.just("item")
        .subscribeOn(Schedulers.computation())
        .subscribeOn(Schedulers.io())
        .subscribe { doSomething() }

This convoluted but simple code is clearly not obvious for every developer. Will doSomething() be executed on the IO or the Computation scheduler?

Actually it'll be the Computation thread, basically because it's the last subscribeOn in the reverse order.

![](/pictures/RxJava_Puzzle_2x_subscribeOn.png)

If this example looks silly, let's look at a more concrete and subtle example

    class MyRepository {
    	val api = //...
    	fun getDataFromNetwork() = api.getData().subscribeOn(Schedulers.io())
    }

    class MyViewModel {
    	val repository = MyRepository()
    	fun getViewState() = repository.getDataFromNetwork()
    	    	.subscribeOn(Schedulers.computation())
    		    .map { data -> computeViewState(data) }
        fun computeViewState(data: ...) = /* Something that should run on a computation thread */
    }

    class MyFragment {
    	val viewModel = MyViewModel()
    	fun onViewCreated() {
    		viewModel.getViewState()
    		    .subscribe { viewState -> updateView(viewState) }
    	}
    }

Just trying to keep things simple in this example, there is many issues here, but let's focus on the ViewModel.

When you look the ViewModel, you'll have the feeling that you computeViewState method will be executed on Computation. At least, it's the intent perceived most of the time, and it's normal since your read it. Unfortunately, the computeViewState is actually done in the IO thread as we learned before, but it's not even visible on the ViewModel class!

Another way to ends with this issue, let's say your repositories never defined the subscribeOn before. Then your code was running on a Computation thread as exepected. But due to parallel network connection limited by Computation (bounded thread pool), you decide some months later to move all network calls to IO thread by adding the `subscribeOn(Schedulers.io())`. Unfortunately, one of your ViewModel was requiring to run on computation and you just break the production code of a non-modified file!

I've experienced these issues myself, it's not science-fiction, and maybe your code also have this problem? So how can we simply avoid that? 

## Rule 1: Use only 1 subscribeOn for each stream, and only when you create the stream.

You don't want your readers (including yourself) asking about which subscribeOn is really used or navigating in many files to understand the threading logic.

For example, if you use Retrofit for a network call, you can ensure the network call is always done in the IO scheduler:

    Retrofit.Builder()
        .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))

If you create a stream for a non-RX library:

    Observable
        .create { emitter ->
        	externalLib.doSomething(listener = { data ->
        		emitter.onNext(data)
        		emitter.onComplete()
        	})
        }
        .subscribeOn(Schedulers.computation())

Defining the subscribeOn at the very beginning of your stream ensure it cannot be changed by anyone else. As most of the business / IO code you write should not be executed on the main thread to avoid freezing your UI, I'd advise to always define a subscribeOn when creating a stream.

I even think that only the wrapper classes that interacts with something external to your code (external libs / Android SDK) should define the threading. 

## Rule 2: Use the 3rd parameter of timer & interval methods instead of subscribeOn()

[Another article](/2020/04/29-RxJava-Puzzle-Scheduler-3rd-param/) explains what are the differences and why you should use this parameter.

Should you explicit the Schedulers.computation() if it's already the default value in RxJava?

It depends of your team's knowledge on Rx. It could be ok to think with a team of experts you don't need to be explicit, but what if in some months you hire a more junior (on RX) developer? Being explicit is a bit of verbosity to ensure you clearly expressed the intent. As threading and asynchronism are particularly difficult topics, I'd strongly advise to being explicit instead of relying on the level of knowledge of your current team.


## Rule 3: Use observeOn instead

Use observeOn instead of subscribeOn when you want to run a part of your code in a specific thread pool. observeOn will **ensure** all the code below this line will run on the given scheduler, so it's what you expect when reading AND it cannot be override by someone else. The new code:

    class MyViewModel {
    	val repository = MyRepository()
    	fun getViewState() = repository.getDataFromNetwork()
    	    	.observeOn(Schedulers.computation())
    		    .map { data -> computeViewState(data) }
        fun computeViewState(data: ...) = /* Something that should run on a computation thread */
    }

Here the intent reflects what's happening, and a change in the repository will have no impact on the ViewModel implementation. This approach ensures your encapsulation is well-decoupled and only modified files will have a new behaviour. As an example, if you decide to add a cache mechanism in your Repository:

    class MyRepository {
    	val api = //...
    	val cache: Data? = null
    	fun getDataFromNetwork(): Single = 
    	    if (cache == null) 
    	    	api.getData()
    	    	   .subscribeOn(Schedulers.io())
    	    	   .doOnNext {data -> cache = data}
    	    else 
    	        Single.just(cache)
    }

Here you avoid a network call, and as such you don't really need to subscribe on IO scheduler, because you're not performing IO operations. Adding a subscribeOn here will just adds a thread swap and not bring any value.

Thanks to the observeOn usage, the ViewModel will NOT be impacted by this change.

## Rule 4: Don't rely on other classes to define your threading.

**Rule 3** examples implies directly **Rule 4**.

    class MyRepository {
    	val api = //...
    	fun getDataFromNetwork() = api.getData().subscribeOn(Schedulers.io())
    }

    class MyViewModel {
    	val repository = MyRepository()
    	fun getViewState() = repository.getDataFromNetwork()
    	    	.observeOn(Schedulers.io()) // Not required, should I remove that line?
    		    .map { data -> storeViewStateOnDisk(data) }
        fun storeViewStateOnDisk(data: ...) = /* Something that should run on an IO thread */
    }

Here the observeOn in ViewModel is not necessary and can even be considered as redundant, because the stream already provides the item on IO scheduler.

Again you have to think for the future modifications of the code. Here you have the 2 classes  visible in a couple of lines, this is not true in a real project. In some months, adding a cache in the repository will change the threading of the ViewModel, if you don't protect yourself with an observeOn. Something as simple as writting in a SharedPref is doing an IO operation, so let's say that the subscribe is done from the main thread (as often) and get data instantly from the repository due to cache, then your app will actually do an IO operation on the main thread (when there is some cache and if you removed the observeOn). 

This problem is quite subtle to detect, you didn't change the ViewModel in question, the ANR is probably not reproducible since you've to emulate an overload of the sdcard IO, and reproducible only when you hit the cache. You're good to lost hours and even days of investigation on this kind of issues.

Just protect yourself, use an observeOn() before any operation requiring a specific scheduler.

### Ok, but changing thread has cost right?

Yes, this solution will have a slight impact on your performance, it's a trade off.

Cons of using observeOn:
- You can lost some micro seconds when switching thread for no reasons

Pros of using observeOn:
- You avoid potential ANR for your clients
- You saved yourself hours of debugging
- You are confident on the behaviour of your code
- Your code is future-proof.


---

Pretty sure there can be other approaches or guidelines on this matter, please share yours!
