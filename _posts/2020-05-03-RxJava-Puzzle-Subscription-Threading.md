---
title: "RxJava2 puzzle: Subscription threading"
subtitle: "Understanding subscribeOn is required, but that's not always enough."
layout: post
background: "/pictures/Android-ImageSpan-Autosizing-background.png"
date: 2020/05/01
---

Some months ago during a code review, I asked a develop to avoid doing IO calls on the main thread (creating laggy UI), by adding a subscribeOn(IO). The code was globally similar to that simplified version:

    var count = 0
    fun doSomething() {
        Observable.fromCallable { retrieveDataFromStorage() }
            .subscribeOn(Schedulers.io()) // Added after the review
            .subscribe(::handleCount)

        println("$count")
        count++
    }
    fun retrieveDataFromStorage() = count // Simplified

handleCount implementation is not relevant here, it's just to give some meaning to the code.

Can you spot the error?

Scroll down for the explanation

<br>
<br>
<br>
<br>

---

# Explanation?

Actually the above code has a random behaviour. 

Without the subscribeOn, the value received was always 0.

With the subscribeOn, the value received was a random between 1 or 0.

So the original developer experimented and decided to replace `fromCallable` by `just`, and the bug disappeared!

    var count = 0
    fun doSomething() {
        Observable.just(retrieveDataFromStorage())
            .subscribeOn(Schedulers.io())
            .subscribe(::handleCount)

        println("$count")
        count++
    }
    fun retrieveDataFromStorage() = count

And here comes the real problem 😅

Can you spot the error now?

<br>
<br>
<br>
<br>

---

# Explanations!

With the fromCallable, the Callable `{ retrieveDataFromStorage() }` was resolved on the subscription. So the `count` value is interpreted by an IO thread, sometimes before, sometimes after the increment. Before or after depends of the time of execution of the `println()`and the time requires to change from main to IO thread.

When a lambda is used in a RX stream, with `::pointerFunction` or with `{ function() }` you have to think about the threading. 

Here by using just() without the lambda, the method `retrieveDataFromStorage` is executed immediately (counter=0) in the main thread and we pass the result of this execution to the `just` operator instead of passing a lambda to execute retrieveDataFromStorage later.

And this code is way worse than the original version, because now we had a line of code that tell "don't worry, you're subscribing in the IO thread". Ok, but the subscription code does nothing but pushing the value given in parameter, it's not executing the lambda anymore.

# How to fix this?

This depends a lot of the context, but I cannot omit this point of the article.

This could be a valid approach for example:

    fun doSomething() {
        Observable.just(retrieveDataFromStorage())
            .subscribeOn(Schedulers.io())
            .subscribe {
                handleCount()
                count++
            }
        println("$count")
    }

But then the count is **maybe** not incremented at the end of the method, again depending of the execution time of `println` vs `subscribeOn`.

Another simple approach, move all the code in the subscribe, so that the related logic is executed synchronously:

    fun doSomething() {
        Observable.just(retrieveDataFromStorage())
            .subscribeOn(Schedulers.io())
            .subscribe {
                handleCount()
                count++
                println("$count")
            }
    }


Most of the time it's because the original code is not implemented in a thread-safe approach like in this example, and those proposals doesn't fix entirely the problem.

Some tips that can help you in this kind of cases:
- **Stay in a stream.** Pure reactive programming doesn't allow side effect (like `count++`), all the code is in the functions, and the immutable data transit from one method to another. Pure reactive can be hard to implement and not a pragmatic solution in my limited experience. But if you can architecture your code so that you're just adding operators with no side-effects from the source of truth to your view, you're probably safe.

- **Debug with logs.** Seems silly but actually debugging with breakpoints will probably make your issue disappear. Also keep in mind that printing on std ouput is not free, so adding log can also make the bug disappear.

- **Learn thread-safe patterns.** This is a very old topic and there is many ways to deal with thread-safety, but it's good to understand that most of the code we write is NOT thread-safe and could lead to random issues. Just an example with an increment on a variable:

```
    var count = 0
    fun main() {
        val threads = (0..99).map {
            Thread(Runnable {
                (0..99).forEach {
                    incrementCount()
                }
            }).apply {
                start()
            }
        }
        threads.forEach { it.join() }
        println("count=$count")
    }
    fun incrementCount() { // NOT thread-safe
    	count++
    }
```

This code prints a value between 100 (theoretically) and 100000, because the operator '++' do 3 things, it reads the value then increment, then store the value in the "shared" variable. So 2 threads could read the value at the same time (ex: 3), increment to get the same value (ex: 4), and store twice this value, instead of storing 5. Eventually

- **Keep one source of truth.** It's not really a solution for the original example but we had 2 sources, the RAM (with the cached data) and the IO (via a SharedPreferences). Due to main of your code being not thread-safe, you could end up with different values in different ViewModel reading the same data on disk.

