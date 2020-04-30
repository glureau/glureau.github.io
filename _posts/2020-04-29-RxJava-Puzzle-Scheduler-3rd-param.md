---
title: "RxJava2 puzzle: Scheduler as 3rd parameter"
subtitle: "Should we use the scheduler as 3rd parameter for methods like timer/interval?"
layout: post
background: "/pictures/Android-ImageSpan-Autosizing-background.png"
---

**On which thread `doSomething()` will be executed for these 3 cases?**

```
    Completable.timer(10, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .subscribe { doSomething() }
```
```
    Completable.timer(10, TimeUnit.MILLISECONDS)
        .observeOn(Schedulers.io())
        .subscribe { doSomething() }
```
```
    Completable.timer(10, TimeUnit.MILLISECONDS, Schedulers.io())
        .subscribe { doSomething() }
```


*Yes there is a trap, response is at the end of the article to avoid spoiler.*


###### This example use Completable as it's the simplest form, but the behaviour is the same for Observable, Single, Maybe and Flowable.
<br>

---

# RxJava2 Schedulers

`Scheduler` implementations in this post can be simplified to a group of threads.
`Schedulers.io()` for example will create a new thread each time it's needed (no limit on the count), while `Schedulers.single()` re-use always the same Thread.

# RxJava2 observables subscription

First we need to know some things about how works the subscription.

All classes that extends Completable / Observable / etc have to implement a `subscribeActual` method. This method is called when the stream is actually subscribed.

	val stream = Completable.just(1) 
	// subscribeActual is not called yet at this point
	stream.subscribe() // This code runs subscribeActual.

The `subscribeActual` is called from the thread that calls subscribe(), so in an Android view, it means the code inside this method actually runs on the Main thread. This is perfectly fine indeed, as the scheduling is not blocking.

Also if you want this code to be run on another thread, you can use subscribeOn().

# How is scheduling implemented?

If we open the RxJava2's CompletableTimer, we can notice that the scheduling code is done in the `subscribeActual`. 

    @Override
    protected void subscribeActual(final CompletableObserver observer) {
        TimerDisposable parent = new TimerDisposable(observer);
        observer.onSubscribe(parent);
        parent.setFuture(scheduler.scheduleDirect(parent, delay, unit));
    }

Behind the scene, RxJava2 is using [`ScheduledExecutorService#schedule`](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledExecutorService.html#schedule(java.lang.Runnable,%20long,%20java.util.concurrent.TimeUnit)). The internal implementation is provided directly by Java and it handles the scheduling via an [Executors](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Executors.html).

Thanksfully, the low-level implementation is not very relevant, but we can note that the RxJava2 code is not actively waiting but only delegating the work, so the thread calling scheduler.scheduleDirect() does **not** matter here. What we want to know is on which thread the `Future` result will be sent.

# The 3rd parameter

The 3rd paramter of the timer like methods is the scheduler. By default this scheduler is `Schedulers.computation()`. The method documentation says about this param:

```text
  scheduler - the scheduler where to emit the complete event
```

This means the complete event, and so the lambda containing our code, will be called on one of the thread of the given scheduler.

    Completable.timer(10, TimeUnit.MILLISECONDS)
        .subscribe { /* this code actually runs on a Computation thread */ }

# The `subscribeOn` case

Let's have a look again at the subscribeOn version:

    Completable.timer(10, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .subscribe { doSomething }

With the previous knowledge, we can deduce that:
- due to the subscribeOn, the scheduling code will be done in an IO thread
- then the result will be given to one of the Computation thread
- so the **`doSomething()` actually runs on one of the computation thread**.

Clearly this is dangerous code: the perceived intent is that this code should run on the IO scheduler but it's not.

# The `observeOn` case

The `observeOn` operator changes the thread of the underneath stream. 
In this case, this means that the completed event sent from the timer will change thread during the observeOn, to a IO thread.

    Completable.timer(10, TimeUnit.MILLISECONDS)
        .observeOn(Schedulers.io())
        .subscribe { doSomething() }

As a result:
- the timer emits the complete event on a Computation thread,
- the observeOn change from Computation to an IO thread (performance cost),
- the `doSomething()` actually runs on one of the IO thread.

This is better as the execution reflects the perceived intent.

# The `3rd parameter` case

    Completable.timer(10, TimeUnit.MILLISECONDS, Schedulers.io())
        .subscribe { doSomething() }

Eventually if we only use the 3rd parameter:
- the timer emits on an IO thread
- the `doSomething()` actually runs on one of the IO thread.


# Conclusion

`subscribeOn` will execute the code on a Computation thread, **not the expected behaviour**.
`subscribeOn` is generally an operator that is badly interpreted by many developers, more article to come on that topic!

`observeOn` will execute the code on the IO thread as exepected, but will add an unnecessary change of thread.

Using the 3rd parameter do what you expect and have the minimal performance cost.


---

# Want to test yourself?

    @Test
    fun the3rdParameter() {
        Completable.timer(10, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .subscribe { println("subscribeOn: " + Thread.currentThread()) }

        Completable.timer(10, TimeUnit.MILLISECONDS)
            .observeOn(Schedulers.io())
            .subscribe { println("observeOn: " + Thread.currentThread()) }

        Completable.timer(10, TimeUnit.MILLISECONDS, Schedulers.io())
            .subscribe { println("timer: " + Thread.currentThread()) }

        Thread.sleep(100)
    }
    // subscribeOn: Thread[RxComputationThreadPool-1,5,main]
    // observeOn: Thread[RxCachedThreadScheduler-2,5,main]
    // timer: Thread[RxCachedThreadScheduler-1,5,main]

