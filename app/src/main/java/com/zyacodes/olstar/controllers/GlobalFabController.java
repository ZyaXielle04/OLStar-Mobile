package com.zyacodes.olstar.controllers;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.zyacodes.olstar.R;

public class GlobalFabController {

    public static void attach(Activity activity, View.OnClickListener listener) {

        // Get root view of the activity
        FrameLayout rootView = activity.findViewById(android.R.id.content);

        // Inflate FAB layout
        View fabView = LayoutInflater.from(activity)
                .inflate(R.layout.view_fab, rootView, false);

        FloatingActionButton fab = fabView.findViewById(R.id.globalFab);

        // Set click behavior
        fab.setOnClickListener(listener);

        // Prevent duplicates
        if (rootView.findViewById(R.id.globalFab) == null) {
            rootView.addView(fabView);
        }
    }
}