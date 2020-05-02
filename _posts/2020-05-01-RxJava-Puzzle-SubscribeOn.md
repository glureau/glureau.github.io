---
title: "RxJava2 puzzle: Do you understand subscribeOn?"
subtitle: "In my experience, the operator that has the lowest degree of understanding, and could definitely backfire at you in unexpected ways."
layout: post
background: "/pictures/Android-ImageSpan-Autosizing-background.png"
---

In this post I'll display the response in white just after the questions, take time to challenge yourself before selecting the text to see the response ;)

By default I'll consider the code executed from the main thread.

---

Just as a remember, the basic use case:

    Observable.just("item")
        .subscribeOn(Schedulers.io())
        .subscribe { doSomething() }

Here the `doSometing` method is called on one of the thread of the Schedulers.io().

# Puzzle 1 - subscribeOn + observeOn

    Observable.just("item")
        .observeOn(Schedulers.io())
        .subscribeOn(Schedulers.computation())
        .subscribe { doSomething() }

A - Main thread

B - Computation

C - IO

D - It's not executed

---

<p style="color:white;">
IO - the subscribeOn operator impacts the start of the stream, until an observeOn changes the thread. The order of the subscribeOn is irrelevant.
</p>

---

# Puzzle 2 - 2 subscribeOn

    Observable.just("item")
        .subscribeOn(Schedulers.computation())
        .subscribeOn(Schedulers.io())
        .subscribe { doSomething() }

A - Main thread

B - Computation

C - IO

D - It's not executed

---

<p style="color:white;">
Computation - the subscribeOn operator only consider the very first subscribeOn and ignore the other ones, so order matters.
</p>

---

# Puzzle 3 - subscribeOn + 2 doOnSubscribe

`doOnSubscribe` offers the opportunity to react when the subscription occurs... but on which order is it executed?

    Observable.just("item")
        .doOnSubscribe { foo() }
        .doOnSubscribe { bar() }
        .subscribeOn(Schedulers.computation())
        .subscribe()

A - foo() then bar()

B - bar() then foo()

C - only foo() is executed

D - It's not executed

---

<p style="color:white;">
foo() then bar() - Same order than when you read the stream, convenient... right?
</p>

---

# Puzzle 4 - 2 subscribeOn + 2 doOnSubscribe

What if we add another subscribeOn

    Observable.just("item")
        .doOnSubscribe { foo() }
        .subscribeOn(Schedulers.computation())
        .doOnSubscribe { bar() }
        .subscribeOn(Schedulers.computation())
        .subscribe()

A - foo() then bar()

B - bar() then foo()

C - only foo() is executed

D - only bar() is executed


---


<p style="color:white;">
bar() then foo() - Opposite order than the previous method! 
</p>

<p style="color:white;">
Feel lost? Let's do one more step before the explication
</p>

---

# Puzzle 5 - 3 subscribeOn + 2 doOnSubscribe

Let's add another subscribeOn, and think about the threading on which foo() and bar() are executed.

    Observable.just("item")
        .subscribeOn(Schedulers.single())
        .doOnSubscribe { foo() }
        .subscribeOn(Schedulers.computation())
        .doOnSubscribe { bar() }
        .subscribeOn(Schedulers.io())
        .subscribe()


A - foo() on Single then bar() on Computation

B - bar() on IO then foo() on Computation

C - bar() on Computation then foo() on Single

D - I give up


---

<p style="color:white;">
B - bar() on IO then foo() on Computation
</p>

<p style="color:white;">
You can think the subscription stream from bottom to top, and the doOnSubscribe like a add to a runnable list. Each time you reaches a subscribeOn, you execute the runnables and after that change the thread.
</p>

<p style="color:white;">
The execution of the subscription starts from the bottom and go up. 1st it reaches a subscribeOn so it switch thread, then it reaches doOnSubscribe { bar() } so it adds bar() in a sort of list. Then it reaches another subscribeOn, and here before changing thread it execute its list (so bar is executed). Once the executable list is empty, it will change again from threads, and eventually execute foo()
</p>

---

# Puzzle 5 - subscribeOn + timer

Ok, too much lines, let's restart from simple:

    Observable.timer(10, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .subscribe { doSomething() }

A - doSomething is executed on IO

B - doSomething is executed on Single

C - doSomething is executed on Computation

D - doSomething is executed on main

---

<p style="color:white;">
C - doSomething is executed on Computation
</p>

<p style="color:white;">
Some methods, like timer or interval, takes a 3rd parameter for the scheduler. If you don't define it, the method has a default for you, here it's Computation.
</p>

---

# Puzzle 6 - subscribeOn + Subject

Add some Subject now?

    val subject = BehaviorSubject.create<String>()

    Observable.just(0)
        .observeOn(Schedulers.io())
        .subscribe { subject.onNext("any") }

    Thread.sleep(10)

    subject.subscribe { doSomething(it) }

A - doSomething is executed on main

B - doSomething is executed on Computation

C - doSomething is not executed

D - doSomething is executed on io

---

<p style="color:white;">
A or D - it depends!
</p>

<p style="color:white;">
I've seen this pattern multiple times. The logic behind was  something like 
</p>
<p style="color:white;">
<< if all my network calls are done on IO, then I can publish the result in a Subject and observers of the subject will receive items on IO too >>
</p>

<p style="color:white;">
Unfortunetaly, it's not that simple when the subject has a "memory" like Behaviour or Replay subjects. When you subscribe, if the data is already in memory, you'll receive the data on the main thread, if there was no data yet then you'll receive the data on the IO thread, preserving the thread. So you can use this approach with Subject without memory but it's not really safe as you can see.
</p>
<p style="color:white;">
Right now on my computer, if I change the sleep to 0ms, "any" is received on IO, if I change the sleep to 1ms, "any" is received on main.
</p>
---

If you've experienced some difficulties on these questions, I'll soon write an article on my best practices to avoid these pitfalls.