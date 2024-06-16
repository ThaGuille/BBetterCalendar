package com.example.bbettercalendar.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> timerText;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        timerText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
        timerText.setValue("20:00");

    }

    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<String> getTimerText() {
        return timerText;
    }
}