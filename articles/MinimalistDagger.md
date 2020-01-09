# Minimalist Dagger

Dagger (2) is often considered as too much complex for reasons,
this article will try to propose a really simple approach limiting complexity and learning curve.

It purposefully skip a lot of details, because I consider most of the details have a bad complexity/gain ratio.


## Annotations

### JSR-330

Developed with Java, this set of annotations define how to declare DI and contains:

@Inject : Applied to a constructor or a field, it indicates this dependency will be provided sometimes

@Singleton : Applied to a class, when you want to have only one instance of it.

This de-facto library only declare annotations, it doesn't need apt/kapt, doesn't have any cost to build time, etc.
You can use these annotations with other libraries than Dagger, so having them in your code doesn't mean you are coupled to Dagger
You'll be able to change to another DI (eg. Kodein) when you want to, without modifying all your classes.

### Dagger2


## Related articles & videos

https://www.youtube.com/watch?time_continue=28&v=9fn5s8_CYJI&feature=emb_logo