---
title: "An audio visualization with Jetpack Compose"
subtitle: "Yet another Jetpack Compose article, this time with a bit of Audio, to make your animations a bit more dynamic!"
keywords: android jetpack compose equalizer visualization visualizer
layout: post
background: "/pictures/compose-equalizer.jpg"
---

In this article, you'll learn some bits about Jetpack Compose, Android audio visualizer and how to draw a nice animation based on the audio input. You can download the repository [here](https://github.com/glureau/Equalizer) to check the final result.

As Compose is not stable yet, be aware that stuff could evolve. (I based the article on this [project](https://github.com/glureau/Equalizer), check the exact versions there.)

For the audio data, we'll use the android audiofx [Visualizer](https://developer.android.com/reference/android/media/audiofx/Visualizer) as it provides directly FFT and wave form. No worry, we don't need to understand how it's computed, but just as a quick introduction:
- FFT shows the frequencies used, and it's generally a lot of basses and way less high frequencies, very unbalanced for a shiny animation
- Wave form is [the shape of its graph as a function of time, independent of its time and mag...](https://en.wikipedia.org/wiki/Waveform) well it's a signal way more balanced and dynamic, perfect for our animation.

To use Visualizer, we need 2 permissions:

    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />


And we'll also need to request the runtime permission for RECORD_AUDIO:

    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 42)
    }

(42 is the request id that you'll need to implement properly the permission, not the topic of this article so let's hack this)

Now let's implement a basic code to play a mp3 file from the asset folder


    private var player: MediaPlayer? = null
    private fun play() {
        val afd = assets.openFd("mymusic.mp3")
        player = MediaPlayer().apply {
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            prepare()
            start()
        }
    }

    private fun stop() {
        player?.stop()
        player?.release()
        player = null
    }

And our first JetpackCompose button in the UI to play/pause the player. For now, let's put that directly in the MainActivity, inside the default `setContent {}` (created automatically when creating a new Compose project with an empty Compose activity).
If you're not clear on the Jetpack Compose usage, it could be the right time to pause the watch some intro first.

        setContent {
            val (isPlaying, setPlaying) = remember { mutableStateOf(false) }
            EqualizerTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Button(onClick = { setPlaying(!isPlaying) }) { 
                        Text(if (isPlaying) "pause" else "stop")
                    }
                }
                if (isPlaying) {
                    play()
                } else {
                    stop()
                }
            }


We're good to instantiate the Visualizer now.

    visualizer = Visualizer(audioSessionId).apply {
        enabled = false // All configuration have to be done in a disabled state
        captureSize = Visualizer.getCaptureSizeRange()[0] // Minimum sampling
        setDataCaptureListener(
            object : Visualizer.OnDataCaptureListener {
                override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
                }
                override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    process(waveform)
                }
            },
            Visualizer.getMaxCaptureRate(), true, true)
        enabled = true // Configuration is done, can enable now...
    }

- The audioSessionId comes from the player.audioSessionId, it allows to select a specific audio stream, so that you only get the data from your stream and avoid notification sound impact. Also you could use the value '0' to get the result of all mixed audio streams.
- The enabled=false is a safe measure if you want to change dynamically capture settings, as all settings operation have to be done in a disabled state (or else it throws an exception).
- The capture size is specific to the hardware (ex: on my current device the range is [128-1024]), here we don't need a lot of data points to have a funky animation yet, so we start low to reduce memory footprint.
- Eventually the callback gives us 2 streams, and we'll only take care of waveform as previously mentionned.

Since the capture size is specific to the device (using a value out of the range will crash), you may want to change the capture size to match your animation sampling size. As we're starting with a basic equalizer composed of 32 columns, we only need 32 data point, so we can re-sample the Visualizer output :

    val resolution = 32
    val processed = IntArray(resolution)
    val captureSize = Visualizer.getCaptureSizeRange()[0] // Same value than in the Visualizer setup
    val groupSize = captureSize / resolution
    for (i in 0 until resolution) {
        processed[i] = data.map { abs(it.toInt()) }
            .subList(i * groupSize, min((i + 1) * groupSize, data.size))
            .average().toInt()
    }
    // processed has the re-sampled data now.

This re-sampling is super basic, but all allowed captures values on the Visualizer should be a power of 2, so if we also take a power of 2 for the resolution we're fine.


On the [github project](https://github.com/glureau/Equalizer), I use my own data class named VisualizerData to store the processing result as it will be handy later, but you can consider a simple IntArray for now.


Now let's draw the equalizer!

Requirements:
- takes all the space available
- draw a row of 32 bars with a little padding between each bar
- animate the height of bars so the animation is smooth

```

    @Composable
    fun BarEqualizer(
        modifier: Modifier,
        visualizationData: VisualizerData,
    ) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Row(modifier.onSizeChanged { size = it }) {
            val widthDp = size.getWidthDp()
            val heightDp = size.getHeightDp()
            val padding = 1.dp
            val barWidthDp = widthDp / visualizationData.resolution

            visualizationData.bytes.forEachIndexed { index, data ->
                val height by animateDpAsState(targetValue = heightDp * data / 128f)
                Box(
                    Modifier
                        .width(barWidthDp)
                        .height(height)
                        .padding(start = if (index == 0) 0.dp else padding)
                        .background(MaterialTheme.colors.primaryVariant)
                        .align(Alignment.Bottom)
                )
            }
        }
    }

    @Composable
    fun IntSize.getWidthDp(): Dp = LocalDensity.current.run { width.toDp() }

    @Composable
    fun IntSize.getHeightDp(): Dp = LocalDensity.current.run { height.toDp() }
```


Yes maths are a bit wrong, the 1st bar is 1dp too large, just for sake of simplicity.
But the intersting part is actually the animation, that is a oneliner without any specific values here.
That's right, `animateDbAsState` takes the new wanted height and will smooth the transition to the new desired height, so that we don't need to care too much.
Indeed you could want to use the full animation API to setup your own AnimationSpec and reduce the sampling frequency.

Let's plug into into our MainActivity: we need to define another state based on the data, and pass it to the new BarEqualizer


    // MainActivity

        setContent {
        val visualizerData = remember { mutableStateOf(VisualizerData()) }
        val (isPlaying, setPlaying) = remember { mutableStateOf(false) }
        EqualizerTheme {
            // A surface container using the 'background' color from the theme
            Surface(color = MaterialTheme.colors.background) {
                Content(isPlaying, setPlaying, visualizerData)
            }
            [...]

    // Content

    @Composable
    fun Content(
        isPlaying: Boolean,
        setPlaying: (Boolean) -> Unit,
        visualizerData: MutableState<VisualizerData>
    ) {
        Column {
            Button(onClick = {
                setPlaying(!isPlaying)
            }) {
                Text(if (isPlaying) "stop" else "play")
            }
            BarEqualizer(
                Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0x40000000)),
                visualizerData.value
            )
        }
    }




That's it folks! Now you can add some fancy colors and add more maths to make it pretty. Let's have some fun


<iframe width="888" height="666" src="https://www.youtube.com/embed/Z5hTpUuXQ94" frameborder="0" allowfullscreen></iframe>




-----

Bonus : [circular equalizer + cubic bezier curves](https://francoisromain.medium.com/smooth-a-svg-path-with-cubic-bezier-curves-e37b49d46c74)
