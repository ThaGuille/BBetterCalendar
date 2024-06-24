package com.example.bbettercalendar.popups;

public interface OnPopupListener<T> {

    void OnClosePopup(int popupType);
    void OnClosePopup(int popupType, T result);
}
