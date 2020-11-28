package com.example.app.ui.device1;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class Device1ViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public Device1ViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is device1 fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}