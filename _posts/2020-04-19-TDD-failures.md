---
title: "TDD failures?"
subtitle: "Discovered old videos about TDD and understood why I failed at it... til now!"
layout: post
background: "/pictures/sarah-dorweiler-x2Tmfd1-SgA-unsplash.jpg"
---

In 10 years of being a developer, I attended multiple "TDD" dojos. They were all great, trying to push some good practices like:
- Tests first
- Red/Green/Refactor
- 100% code coverage
- self-documented

But unfortunately, when I spoke to the attendes or even the organizer some days or weeks after that, I discovered that no-one was using TDD in its day-to-day job!

Why?

Personally, there were some reasons... or feelings to be right:
- Bad reputation "TDD is hard to do right", "if you don't do TDD right, it will only increase your dev time"
- I don't always know what the system is supposed to do, so I can't write tests firsts.
- When I add unit-tests before I refactor, I have to rewrite most of the tests anyway, so they are not really safer if wrote first.
- 100% code coverage looks bad. When I have a data class, or a wrapper, the code is so dummy than the tests are just verbosity. (We don't consider test coverage based on which parts is more likely to have bugs or be refactored, we just want coverage everywhere.)
- Each time I had to change some piece of code that has good coverage, the self-documentation was unclear, because it was more about what is going on, then why is this required in the first place.
- Test code is a copy-cat of the code under test! When using mocks and testing every single lines, tests are just another way to define the code. It's fine in the way it push against regressions, but it also push against changes. Maybe it's because I don't follow Open/Closed principles properly, but it can be hard to know by advance how the code will evolve. Eventually when a lot of the code have to be re-written, the unit tests are just dropped (keeping just the test names to provide a close case coverage).
- Don't open your private methods with @VisibleForTesting. You're breaking the encapsulation to test something that anyway, will never be called this way. (Frankly I'd be better off without this annotation, in my opinion, it's breaking one of the more important concept of OOP.)

Finally I discovered this talk, thanks to Twitter, and it explained to me why it feels wrong. Spoiler: I'm doing unit test wrong, the dojos were not focused on the more important pieces of TDD.

<https://www.youtube.com/watch?v=EZ05e7EMOLM>

Is TDD Dead? (David Heinemeier Hannson / Martin Fowler / Kent Beck)
<https://martinfowler.com/articles/is-tdd-dead/>
<https://www.youtube.com/watch?v=z9quxZsLcfo>
<https://www.youtube.com/watch?v=JoTB2mcjU7w>
<https://www.youtube.com/watch?v=YNw4baDz6WA>
<https://www.youtube.com/watch?v=dGtasFJnUxI>

The things I'll try to remember and apply for now on:
- "test first" is just a way to do TDD, it applies well to specific case, but it's clearly not the only way to go. The goal is to have confidence in our code by delivering a test suite, doesn't matter how we get there.
- Apply unit test to a higher level, a unit test can imply multiple methods/classes, so refactoring can effectively become easier.
- Better unit tests means less unit tests (at least on my limited experience)
- case coverage != code coverage: 
  - Defines UT from business spec, no more no less.
  - Cases defined by the busincess/customer should always work. If some parts of the code can be out of the defined cases: check with business to add a new test acceptance, remove the code, or let it untested.
  - Don't get obsessed with code coverage.
- Architecture should be very cautious about adding indirection/complexity for the sake of testability.
- Indirection (to be able to test, or induced by a "well-designed architecture") add complexity and psychological load, making anything harder for the next developer to read/understand/fix/change/improve. So adding tests can actually makes everything worth...
- A mock that returns a mock that returns a mock...: your class under test relies on other class hierarchy, it's not good encapsulation and is a smell.
- "Unit" is not defined **on purpose**. Each team is responsible to define the test style for which is best for the project and the team.
- (personnal deduction) The psychology of each developer impacts how the code is architectured and if each methodology is applicable or not. Forcing in some ways is not the right approach, but sharing some good practices in the team is indeed critical (for the project and for each developer).
- coupling directly opposed to cohesion? Nice thought from DHH, need to go deeper on this topic.
- "Hard to test" often means "bad design"
- Increase Test fidelity (use case coverage) -> Increase cost of the feature (time to test, maintenance) so should be evaluated cautiously.

As always in developping, all is about trade-off, it's a difficult balance to adjust all the time.

There is no silver bullet, try new approaches and pick what fit best for you.
