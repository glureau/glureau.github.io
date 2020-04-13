---
title: "Android ImageSpan autosizing"
subtitle: "Story: How we use Java reflection to achieve autosizing text & images while aligning multiple lines."
layout: post
background: "/pictures/Android-ImageSpan-Autosizing-background.png"
---

Hey there! 

This week at Betclic, I had to implement a very simple layout as you can see:

![](/pictures/Android-ImageSpan-Autosizing-0.png) 
> *Nop sorry, that's not the real app screen ;)*

Ok, what's our business constraints here?
- 3 texts received from the network (translated on the backend)
- a tag **&lt;picto&gt;** in the 3rd string to indicate the position of the picture
- the 3 texts have to be aligned on left and right and use as much space as possible (as you can see above), inside a defined range (min/max)

As you may already know, Android offers two pieces made for this kind of stuff:
- **[Autosizing TextView](https://developer.android.com/guide/topics/ui/look-and-feel/autosizing-textview)**, selects the best font size to fill the available space
- **[ImageSpan](https://developer.android.com/reference/android/text/style/ImageSpan)**, to insert a Drawable/Bitmap in a text

This should be EASY! Right?

# Autosizing limitations

I start to create a ConstraintLayout with 3 TextViews. The 1st TextView is constrained to **parent** on start/top/bottom with some margins, the 2 other views are topToBottom of the 1st and 2nd view as you can expect.

As it's fully constrained horizontally, it's recommended to use `layout_width="0dp"` and since I want the 3 texts to be regrouped on the vertical axis, I started with `layout_height="wrap_content"`. Finally I enabled autosizing with `app:autoSizeTextType="uniform"`, let's see the beautiful result!

<script src="https://gist.github.com/glureau/149b9e325d196872439534f6e532de2a.js"></script>

![](/pictures/Android-ImageSpan-Autosizing-1.png)

Did I miss something? Oh yes, the autosizing takes all the place available on BOTH axes. Here it has more space on the horizontal axis, but on the vertical axis it's limited by our `wrap_content`…
May produce unexpected results? No, it certainly produces unexpected results :) 

![](/pictures/Android-ImageSpan-Autosizing-2.png)

Makes sense but not helpful, I wish we could select a type `horizontal` instead of `uniform`.

> So if I want to autosize a line, I need to provide the height… but how? Since my content is dynamic, did I need to compute the height myself? And if I need to compute it, why the point to use autosize at all?

Well ok, let's forget the height for now and include the picture with ImageSpan, so we have all elements for the compute.

# INCLUDE GIST

![](/pictures/Android-ImageSpan-Autosizing-3.png)

ImageSpan constructor offers a 2nd parameter to align your ImageSpan, but it align the bottom of the picture with the bottom or the baseline of the font. Here I want to align on the baseline but also align the top of the picture with the ascent. (If you want a [nice explanation about baseline/ascent/descent](https://proandroiddev.com/android-and-typography-101-5f06722dd611).)

-----------------------

# Custom fonts / Emojis

Custom fonts allows you to add vector icons from a unicode. to Yes the need is very similar to emojis so we could have created a custom font, it could have matched our needs pretty easily, but it has also some drawbacks:
- the font file is binary, so not easy to diff when a change is done on it
- as it's a binary file, merging 2 branches just requires devs to talk each others about the changes and create the font together, so not as fluid as 2 distinct drawable xmls.
- changing the colour of the icons based on the build variant requires to generate multiple fonts, one for each variant
- every images need to be reworked in vector format to be included in a font (that's good for the final quality indeed, but has a cost)
- we can't use remote images, so all have to be ready at build time, and updating a picture in the image requires rebuilding the app and waiting for the store approval.

But if you don't care about these drawbacks or have straegies to mitigate them, I heavily recommend to use a custom font.

-------------------------

# Compute the size myself

At this time I thought it should not be that difficult to compute a scale ratio manually and apply it to the text and the picture.

- Measure the text width with random font size.
Use `textView.paint.getTextBounds("mytxt", 0, 5, bounds)` to extract the width of the text
- Use the `TextView.width` to determine the available space
- `textSize=testSize * (textViewWidth / textBoundsWidthFloat)`

The ratio is the scale to apply to the text size so the next call to getTextBounds should return the width of the TextView…


![](/pictures/Android-ImageSpan-Autosizing-4.png)
> Please don't do that


We've pushed the idea to compute the TextView height,  get an **almost** stable result, but in some cases with our different fonts, there were errors due to floating compute precision, font inter-char spacing. Even with a magic const 0.97 to hide errors, we discovered on low-end devices that the layout was broken (text on 2 lines, too big margins, words disappearing).

Also the other limitations of this almost-ok approach :
- you have to re-implement the min/max/granularityStep if you need these features (if you want to be safe no matter the weird data you can receive)
- the compute is relatively simple IF there is only one line, but if you want a 2 lines text with 3 picto you then need to understand on which line you've the pics… not so funny, not scalable
- doesn't support lineSpacing extra, multiplier, breakStrategy

-----------------------------------------------------

# AppCompatTextViewAutoSizeHelper

Some things to know about this class:
- Declared in the support library, define the autosizing mechanism
- Does NOT compute the best size directly, instead, it uses min/max/granularity to generate an available sizes array and run a binary search until it founds the largest font size that doesn't overflow the lineCount or the height of the View. It uses a `StaticLayout` and call the measurement on it. No render needed so no artifact or big impact on perf.
- they have to support autosize features down to api 14 (autosize is supported natively only since api 26)
- they use reflections to access TextView private methods 😢
- most of the methods of the AppCompatTextView (and helpers inside) are also restricted so we cannot use them… unless we also use reflections.

 > I dislike reflection code, it's so fragile that you can break your layout when updating the lib without anyone noticing the problem, until it's in production. Especially for UI, the behavior can slightly change, crop a text, and most UI tests will still be green…

Anyway, too much time spent to align 3 texts and an image, we need a solution, so go for Reflection as the support library does the same.

# INCLUDE GIST

A simple copy paste of the 2 methods and I'm able to get the available size computed by the autosize mechanism.

Now that we have the computed value, let's see the interesting part, the `suggestedSizeFitsInSpace` that we need to modify for our needs


# INCLUDE GIST

So 2 problems here: 
- it's computing the height overflow and I don't care
- it's not updating the ImageSpan sizes but only the text size.

For the 1st problem, an easy solution is to create another method `suggestedSizeFitsInWidth` that call the above method with `RectF(0,0,availableWidth, 1024*1024)`. It simulates I've a super long height to do the compute, so the final condition is skipped. Why `1024*1024`? It's ported from `TextView#VERY_WIDE` that represents a maximum width in pixels the TextView takes when horizontal scrolling is activated.

Advantage of this approach, no code modification of the original method, so I know it's supposed to work.

For the 2nd problem, I need to update the original code, so just after the `setTextSize(suggestedSizeInPx)` I add this line of code:
`alignImageToText(tempTextPaint, drawableHeightComputeMode)`

# INCLUDE GIST

I copy pasted the binary search of the AppCompat library, since we add an additional parameter the underneath method wasn't easily re-usable.


# XML values storage

So now we can get the autoSize XML values from the AppCompatTextView to run our binary search but we've to take somethings into account here. 

You've to define the autosize type to uniform if you want the AppCompatTextView generate the available sizes array. This array is used by the binary search to find the best font size.

When we use the setTextSize method, the parameters are ignored if the autosize is running.

So we need to enable > compute > disable > setSize, and if the method is called twice, since the disable clean the XML values, we need to store them somewhere…

That's unfortunate as we've moved to an extension function implementation, now we need to store value to the TextView, and we cannot add/store value via Reflection, and I don't like overriding TextView as it's quickly not scalable. So here is a quick hack: we use a WeakReference on the view itself, and then compare the view to restore the previous values.

# INCLUDE GIST

Ok so let's have a look at the final extension function now:

# INCLUDE GIST


# Final result

![](/pictures/Android-ImageSpan-Autosizing-0.png)

As you can notice, we're clearly not done yet, the custom font is not loaded (but there is enough tutorial about that), there is too much space between lines (next article maybe?)… BUT for this first article, the left and right are aligned, the picto is adjusted, and the final implementation re-use autosize parameters and doesn't consume many more resources than a standard autosize.
You can grab the full code from this sandbox project: https://github.com/glureau/atvasis