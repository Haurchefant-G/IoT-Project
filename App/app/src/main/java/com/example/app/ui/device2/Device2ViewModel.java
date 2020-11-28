package com.example.app.ui.device2;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class Device2ViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public Device2ViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is device2 fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}