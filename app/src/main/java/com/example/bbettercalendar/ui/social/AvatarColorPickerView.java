package com.example.bbettercalendar.ui.social;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

/**
 * F1-u5: selector de los 8 colores de avatar ({@link AvatarPalette}), 2 filas de 4 dibujadas en un
 * canvas. El elegido lleva un anillo. Cada celda mide al menos 56dp de alto para ser fácil de tocar.
 */
public class AvatarColorPickerView extends View {

    public interface OnColorSelectedListener {
        void onColorSelected(int index);
    }

    private static final int COLUMNS = 4;
    private static final int ROWS = AvatarPalette.SIZE / COLUMNS;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float swatch;
    private final float ringWidth;
    private final float cellHeight;

    private int selected;
    @Nullable private OnColorSelectedListener listener;

    public AvatarColorPickerView(@NonNull Context context) {
        this(context, null);
    }

    public AvatarColorPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        swatch = getResources().getDimension(R.dimen.social_picker_swatch);
        ringWidth = getResources().getDimension(R.dimen.social_picker_ring);
        cellHeight = getResources().getDimension(R.dimen.social_picker_cell_height);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringWidth);
        ring.setColor(ContextCompat.getColor(context, R.color.bb_on_surface));
        setFocusable(true);
        updateDescription();
    }

    public int getSelectedColor() {
        return selected;
    }

    public void setSelectedColor(int index) {
        int clamped = AvatarPalette.clamp(index);
        if (clamped != selected) {
            selected = clamped;
            updateDescription();
            invalidate();
        }
    }

    public void setOnColorSelectedListener(@Nullable OnColorSelectedListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = Math.round(cellHeight * ROWS) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellW = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) COLUMNS;
        for (int i = 0; i < AvatarPalette.SIZE; i++) {
            float cx = getPaddingLeft() + cellW * (i % COLUMNS) + cellW / 2f;
            float cy = getPaddingTop() + cellHeight * (i / COLUMNS) + cellHeight / 2f;
            fill.setColor(AvatarPalette.color(getContext(), i));
            canvas.drawCircle(cx, cy, swatch / 2f, fill);
            if (i == selected) {
                canvas.drawCircle(cx, cy, swatch / 2f + ringWidth * 1.5f, ring);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float cellW = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) COLUMNS;
            int col = (int) ((event.getX() - getPaddingLeft()) / cellW);
            int row = (int) ((event.getY() - getPaddingTop()) / cellHeight);
            if (col >= 0 && col < COLUMNS && row >= 0 && row < ROWS) {
                int index = row * COLUMNS + col;
                setSelectedColor(index);
                if (listener != null) {
                    listener.onColorSelected(index);
                }
                performClick();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(R.string.profile_color_picker_description,
                selected + 1, AvatarPalette.SIZE));
    }
}
