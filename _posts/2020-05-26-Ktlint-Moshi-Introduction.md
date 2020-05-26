---
title: "Ktlint: a great 1st experience"
subtitle: "Code generation is often perceived as a compile-time security. We're ready to pay it by increasing the build time. But what if a linter was a replacement for compile-time safety while delivering a better code."
keywords: static code analysis ktlint android moshi
layout: post
background: "/pictures/leone-venter-VieM9BdZKFo-unsplash.jpg"
---

*This is some feedback with the first ktlint custom rules I wrote the past week. In this migration I had to deal with Json parsing issues related to minifications and bad Moshi setup, and I fixed them once and for all with some rules. Here is my story.*

---

This month we've decided to re-evaluate the usage of the [Kotshi library](https://github.com/ansman/kotshi). When we picked this library several months ago, Moshi was only good for java classes and wasn't handling kotlin nullability, default value, init blocks and more. But Moshi evolved a lot (v1.9.2 as I wrote), and provides now a support for Kotlin, with 2 options: reflection (moshi-kotlin) or code generation (moshi-kotlin-codegen). Eventually we opted-in for the codegen version (I don't want to debate on this choice here), for 2 main reasons: the "if it build if runs" (ala Dagger) and the minimal setup requirement (adapters auto-detected). Unfortunately the 1st reason was not true.

# Codegen is not always safe

### Moshi, where is my @JsonClass?

Here is the example from the [Moshi documentation](https://github.com/square/moshi#codegen) :

    @JsonClass(generateAdapter = true)
    data class BlackjackHand(
        val hidden_card: Card,
        val visible_cards: List<Card>
    )

This looks fine right? Let me implement Card as naively as possible

    data class Card(rank: Int, suit: Int)

I purposefully forgot the `@JsonClass`, as it's something easy to forgot. Moshi-kotlin-codegen builds an adapter for the `BlackjackHand` that asks to Moshi an adapter for `Card`. But it doesn't check if Card is a kotlin class and if an adapter has also been generated.

Unfortunately **this is crashing at runtime** because Moshi consider (righfully) you should provide an adapter for Card.

Indeed the fix is straighforward...

    @JsonClass(generateAdapter = true)
    data class Card(rank: Int, suit: Int)

but I hope you caught the error before releasing in production. Personnally I rarely use the buildType release when developing...

### Moshi, where is my enum class?

Now let's replace the `suit` by an enum class, translated Java->Kotlin from the [Moshi documentation](https://github.com/square/moshi#built-in-type-adapters):

    @JsonClass(generateAdapter = true)
    enum class Suit {
      CLUBS, DIAMONDS, HEARTS, SPADES;
    }
    @JsonClass(generateAdapter = true)
    data class Card(rank: Int, suit: Suit)

Nop, you can NOT use @JsonClass on an enum class or you'll get an error like:

```text
error: @JsonClass with 'generateAdapter = "true"' can't be applied to com.glureau.moshilint.Suit:
    code gen for enums is not supported or necessary
```

Ok great, it's not necessary as it's a built-in adapter, so I just have to define my enum like this

    enum class Suit {
      CLUBS, DIAMONDS, HEARTS, SPADES;
    }
    @JsonClass(generateAdapter = true)
    data class Card(rank: Int, suit: Suit)

Let's run this code now...

    java.lang.AssertionError: Missing field in b.a.a.b.a
        [...]
    Caused by: java.lang.NoSuchFieldException: DIAMONDS
        [...]

Arg, so the generated code **looks** proguard-safe, and it is on generated adapter, but using `enum class` like this is NOT proguard-safe. 

Here you can have a quick fix with `@Keep` (on each field or in the class).

    @Keep
    enum class Suit {
      CLUBS, DIAMONDS, HEARTS, SPADES;
    }

**EDIT**: [Manoel Aranda Neto](https://twitter.com/marandaneto) suggested to use a rule to your app proguard file instead (`-keepclassmembers enum * { *; }`), and it works great but will keep too much stuff (probably not a problem for a standard Android app though).
In the meantime I found a comment in the [proguard rules from Moshi](https://github.com/square/moshi/blob/master/moshi/src/main/resources/META-INF/proguard/moshi.pro), looks like we're supposed to use `@JsonClass(generatedAdapter = false)`? ([waiting for confirmation...](https://github.com/square/moshi/issues/1147))


# What's the problem?

This article is NOT a rant against Moshi.

The problem is when you have more than 50 DTOs to maintain and other developers adding more functionnalities. Generally we only enable proguard on release build (because of the big impact on build time + the fact it's harder to debug obfuscated code) so 95% of the development time we don't experience this kind of issues. And sometimes the 5% are not enough to catch the errors during development.

Some options to fix this problem:
- Define some best practices, like @Keep on ell your enums. Good, but you can still forget 1 class and experience crash in production.
- Add tests to ensure you can parse correctly all potential json files in release build, but it's very annoying to write, and maybe 1 test will not be written and the missing annotation released in production.
- disable proguard (in a part of your project or completely), it avoids issue for sure, but it also avoids optimization pass, reduction of the apk size, obfuscation... A custom proguard rule have to be maintain when a new module is created, and since it's not something visible when you develop, it's just another point of failure.

Could be great if it could be checked at compile time right? Let's imagine a moment if the configuration issue was able to block a bad PR, and this kind of issue fixed once and for all...

# Lint/Ktlint

## Benefits

Some of you are probably aware of this tool and using it for standard Java/Kotlin style guideline.

The benefits of static code analysis (with a linter):
- it enforces good practices
- can avoid a bad PR to be merged (using Danger for example)
- doesn't require compilation, so super quick to run
- no impact while developing (same build time)
- error is localized so errors are easy to fix (in AndroidStudio, or in Github if you use Danger)
- you can even implement an auto-fix

**But the real power comes from the custom rules!**

Previously we were relying on our QA team to ensure all the DTOs where properly setup. Custom rules offer a way to avoid errors almost for free!

## How to setup a custom rule

There is some good tutorials on the linter and how to configure them, and as I don't have enough experience I'll just share what I know.

Creation of a custom rules takes 4 steps:
- create a java module (for example `ktlint-rules`)
- create a class that extends RuleSetProvider
- create a class that extends Rule
- create a file for your RuleSetProvider to be detected automatically

---

    // File: src/main/resources/META-INF/services/com.pinterest.ktlint.core.RuleSetProvider
    com.my.package.MyRuleSetProvider

    // src/main/java/com/my/package/MyRuleSetProvider.kt
    class MyRuleSetProvider : RuleSetProvider {
        override fun get() = RuleSet("myrules", CustomRule())
    }

    // src/main/java/com/my/package/CustomRule.kt
    class CustomRule : Rule("my-rules") {
        override fun visit(node: ASTNode, autoCorrect: Boolean, emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit) {
            //...
        }
    }

This is so simple! I'm sad to learn that only now, after several years of Java/Kotlin...

## How to write a custom rule

As a team, we've defined some simple rules to apply everywhere:
- all dto classes should be suffixed with `Dto`
- all dto classes should be data class or enum class
- all `data class` dto should define a `@JsonClass(generateAdapter = true)`
- all `enum class` dto should define a `@Keep`

and several others (about immutability or ensuring Retrofit services always use Dto classes), but let's see how we could implement the 2 last ones for this article.

    override fun visit(node: ASTNode, autoCorrect: Boolean, emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit) {
        if (node.elementType == KtStubElementTypes.CLASS) {
            val klass = node.psi as KtClass
            if (klass.name?.endsWith(DTO_SUFFIX, ignoreCase = true) == true) {
                if (klass.isData()) {
                    var foundAnnotation = false
                    var correctArguments = false
                    klass.annotationEntries.forEach { annotation ->
                        foundAnnotation = annotation.shortName.toString() == "JsonClass"
                        if (foundAnnotation) {
                            correctArguments = annotation.valueArgumentList?.arguments?.any { it.text == "generateAdapter = true" } == true
                        }
                        return@forEach // Stop at the first @JsonClass, multiple annotations is not supported.
                    }
                    if (!foundAnnotation) {
                        emit(klass.startOffset, "Dto class '${klass.name}' should uses the @JsonClass annotation", true)
                    }
                    if (foundAnnotation && !correctArguments) {
                        emit(klass.startOffset, "@JsonClass should define the argument 'generateAdapter = true'", false)
                    }
                } else if (klass.isEnum()) {
                    if (klass.annotationEntries.none { it.shortName.toString() == "Keep" }) {
                        emit(klass.startOffset, "Enum should use a @Keep on top of the class to prevent field minification.", true)
                    }
                } else {
                    emit(klass.startOffset, "Class with suffix Dto should be 'data class' or 'enum class'", false)
                }
            }
        }
    }

This code is pretty naive and could be improved a lot. But just with this snippet of code, you ensure that all your classes that ends with "Dto" are proguard-safe.

## How to test it

Ktlint offers a pretty simple API, let's look at a basic example

    @Test
    fun `signal missing @JsonClass annotation`() {
        DtoRule().lint(
            """data class MyDto(val foo: Int)"""
        ) `should contain same` listOf(LintError(1, 1, "my-rules", "Dto class 'MyDto' should uses the @JsonClass annotation"))
    }

That's it!

You can check the rule, the line/column (relative to the file, starting at 1) where the error should point, and the message to display.

## How to implement auto-fix

KtLint provides 2 methods, `ktlintCheck` and `ktlintFormat`. The 1st one display the error and if the error is auto-fixable or not. The second one apply the changes. Sometimes it make sense to propose an autofix, sometimes it's not relevant and the developer have to take the choice itself.

The 2nd parameter of the `visit` method is a boolean named `autoCorrect`, and it's true when we use `ktlintFormat`,  let's say we want to implement the autofix for the missing `@JsonClass`, a simple implementation could be 

    override fun visit(node: ASTNode, autoCorrect: Boolean, ...) {
        [...]
        if (!foundAnnotation) {
            emit(klass.startOffset, "Dto class '${klass.name}' should uses the @JsonClass annotation", true)
            if (autoCorrect) correctJsonClassAnnotation(node)
        }
    }

    fun correctJsonClassAnnotation(node: ASTNode) {
        (node as TreeElement).rawInsertBeforeMe(LeafPsiElement(REGULAR_STRING_PART, "@com.squareup.moshi.JsonClass(generateAdapter = true)\n"))
    }

And now you can run and this will fix all missing `@JsonClass`!

## How to run it

To setup your project:


    // File build.gradle
    buildscript {
        ext.kotlin_version = "1.3.71"
        repositories {
            google()
            maven { url "https://plugins.gradle.org/m2/" }
            jcenter()
        }
        dependencies {
            classpath "com.android.tools.build:gradle:3.6.3"
            classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
            classpath "org.jlleitschuh.gradle:ktlint-gradle:9.2.1"
        }
    }

    // File app/build.gradle
    apply plugin: "org.jlleitschuh.gradle.ktlint"
    ktlint {
        version = "0.36.0"
        android = true
        ignoreFailures = true
        reporters {
            reporter(ReporterType.CHECKSTYLE)
        }
    }
    // [...]
    dependencies {
        // [...]
        ktlintRuleset(project(":ktlint-rules"))
    }

You can know use the command line `./gradlew ktlintCheck`, or create a Run configuration in AndroidStudio: 'Add new configuration' > 'Gradle' > Gradle project: MyProject:app & Tasks: `ktlintCheck`

When used from AndroidStudio, you can click on the build logs to open directly at the location of the report.

# Conclusion

I had some difficulties to find articles on that matter, and I find the documentation and the API hard to use, but the result is here. I'm now able to detect all issues when opening my PR, without even compiling the project, big win. I'm also way more confident in how we parse our Json and how proguard will impact us.

I hope this will motivate you to try to write your 1st rule. If you've already wrote some, I'm interested in any documention on the topic, please leave a message.

If you are interested by the full Moshi rules we're using at Betclic, please contact me via Disqus or Twitter.
