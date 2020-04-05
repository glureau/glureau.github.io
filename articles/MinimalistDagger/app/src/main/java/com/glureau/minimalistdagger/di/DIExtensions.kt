package com.glureau.minimalistdagger.di

import androidx.fragment.app.Fragment
import com.glureau.minimalistdagger.MyApplication

val Fragment.injector: AppComponent
    get() = (requireActivity().application as MyApplication).component
