package com.example.app.ui.device2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;

public class Device2 extends Fragment {

    private Device2ViewModel device2ViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        device2ViewModel =
                ViewModelProviders.of(this).get(Device2ViewModel.class);
        View root = inflater.inflate(R.layout.device2_fragment, container, false);
        final TextView textView = root.findViewById(R.id.text_device2);
        device2ViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }
}
