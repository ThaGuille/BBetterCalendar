package com.example.bbettercalendar.ui.calendar.binders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.bbettercalendar.R;

import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.example.bbettercalendar.ui.calendar.weekview.WeekView;
import com.example.bbettercalendar.ui.calendar.weekview.WeekViewEntity;

import java.util.Calendar;

public class WeekViewItemAdapter extends WeekView.SimpleAdapter<CalendarItem> {

    public interface OnRangeChangedListener {
        void onRangeChanged(Calendar firstVisibleDate, Calendar lastVisibleDate);
    }

    private OnRangeChangedListener rangeListener;
    private final int textOnLight;
    private final int textOnDark;

    public WeekViewItemAdapter(@NonNull Context context) {
        textOnLight = ContextCompat.getColor(context, R.color.bb_on_primary);
        textOnDark = ContextCompat.getColor(context, R.color.bb_on_surface);
    }

    public void setOnRangeChangedListener(OnRangeChangedListener listener) {
        this.rangeListener = listener;
    }

    @Override
    public WeekViewEntity onCreateEntity(CalendarItem item) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(item.getStartMillis());
        Calendar end = Calendar.getInstance();
        long endMillis = item.getEndMillis() > item.getStartMillis()
                ? item.getEndMillis()
                : item.getStartMillis() + 60L * 60L * 1000L;
        end.setTimeInMillis(endMillis);

        WeekViewEntity.Style style = new WeekViewEntity.Style.Builder()
                .setBackgroundColor(item.getColorArgb())
                .setTextColor(ColorUtils.calculateLuminance(item.getColorArgb()) > 0.4 ? textOnLight : textOnDark)
                .setCornerRadius(8)
                .build();

        return new WeekViewEntity.Event.Builder<>(item)
                .setId((long) item.getId())
                .setTitle(item.getTitle() != null ? item.getTitle() : "")
                .setStartTime(start)
                .setEndTime(end)
                .setStyle(style)
                .build();
    }

    @Override
    public void onRangeChanged(Calendar firstVisibleDate, Calendar lastVisibleDate) {
        if (rangeListener != null) {
            rangeListener.onRangeChanged(firstVisibleDate, lastVisibleDate);
        }
    }
}
