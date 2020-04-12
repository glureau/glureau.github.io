package com.glureau.minimalistdagger.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.glureau.minimalistdagger.R
import com.glureau.minimalistdagger.di.injector
import javax.inject.Inject

class NotificationsFragment : Fragment() {

    @Inject
    protected lateinit var notificationsViewModel: NotificationsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        injector.inject(this)
        val root = inflater.inflate(R.layout.fragment_notifications, container, false)
        val textView: TextView = root.findViewById(R.id.text_notifications)
        Toast.makeText(
            requireContext(),
            "Notifications cleared (previous=${notificationsViewModel.count})",
            Toast.LENGTH_LONG
        ).show()
        notificationsViewModel.clearNotification()
        notificationsViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })
        return root
    }
}
