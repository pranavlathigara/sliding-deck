package com.redbooth;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ListAdapter;

public class SlidingDeck extends ViewGroup {
    private final static int ANIMATION_DURATION_IN_MS = 300;
    private final static int INITIAL_OFFSET_IN_PX = 0;
    private final static int FIRST_VIEW = 0;
    private final static float MAXIMUM_OFFSET_TOP_BOTTOM_FACTOR = 0.75f;
    private final static int MAXIMUM_ITEMS_ON_SCREEN = 4;
    private final static int MINIMUM_TOP_BOTTOM_OFFSET_DP = 10;
    private final static int MINIMUM_LEFT_RIGHT_OFFSET_DP = 15;
    private View[] viewsBuffer;
    private ListAdapter adapter;
    private SlidingDeckTouchController touchController;
    private int offsetTopBottom = INITIAL_OFFSET_IN_PX;
    private int maximumOffsetTopBottom;

    public void setAdapter(ListAdapter adapter) {
        this.adapter = adapter;
        viewsBuffer = new View[MAXIMUM_ITEMS_ON_SCREEN];
        attachChildViews();
        requestLayout();
    }

    public SlidingDeck(Context context) {
        super(context);
        initializeView();
    }

    public SlidingDeck(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeView();
    }

    public SlidingDeck(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeView();
    }

    @SuppressWarnings("unused")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SlidingDeck(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initializeView();
    }

    private void initializeView() {
        viewsBuffer = new View[MAXIMUM_ITEMS_ON_SCREEN];
        touchController = new SlidingDeckTouchController(this);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return touchController.onInterceptTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        int viewHeight = MeasureSpec.getSize(heightMeasureSpec);
        int heightMeasureMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMeasureMode == MeasureSpec.AT_MOST) {
            viewWidth = MeasureSpec.getSize(widthMeasureSpec);
            viewHeight = calculateWrapContentHeight();
        }
        setMeasuredDimension(viewWidth, viewHeight);
        configureChildViewsMeasureSpecs(widthMeasureSpec);
    }

    private int calculateWrapContentHeight() {
        int maxChildHeight = 0;
        for (int index = 0; index < getChildCount(); index++) {
            final View childView = getChildAt(index);
            measureChildView(childView);
            if (childView.getVisibility() != View.GONE) {
                maxChildHeight = Math.max(maxChildHeight, getChildAt(index).getMeasuredHeight());
            }
        }
        int itemsElevationPadding = dp2px(MINIMUM_TOP_BOTTOM_OFFSET_DP)
                                                * (getChildCount() - 1);
        int measuredHeight = maxChildHeight + getPaddingTop() + getPaddingBottom() + itemsElevationPadding;
        int measuredOffset = (offsetTopBottom * (getChildCount() -1));
        return measuredHeight + measuredOffset;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childLeft;
        int childTop;
        int childRight;
        int childBottom;
        for (int index = FIRST_VIEW; index < getChildCount(); index++) {
            final View childView = getChildAt(index);
            childLeft = calculateViewLeft(left, right, childView.getMeasuredWidth(), index);
            childRight = childLeft + childView.getMeasuredWidth();
            childTop = calculateViewTop(bottom, childView.getMeasuredHeight(), index);
            childBottom = childTop + childView.getMeasuredHeight();
            childView.layout(childLeft, childTop, childRight, childBottom);
        }
    }

    private int calculateViewLeft(int parentLeft, int parentRight, int childWith, int zIndex) {
        int center = parentLeft + ((parentRight - parentLeft) / 2);
        return center - (childWith / 2);
    }

    private int calculateViewTop(int parentBottom, int viewHeight, int zIndex) {
        int topMinimumOffset = dp2px(MINIMUM_TOP_BOTTOM_OFFSET_DP);
        int viewTop = parentBottom - getPaddingBottom() - viewHeight - (topMinimumOffset
                            * ((getChildCount() -1) - zIndex));
            viewTop -= getOffsetTopBottom(zIndex);
        return viewTop;
    }

    private int getOffsetTopBottom(int zIndex) {
        int result = 0;
        if (zIndex < (getChildCount() - 1)) {
            result = offsetTopBottom * (getChildCount() - (zIndex + 1));
        }
        return result;
    }

    private void configureChildViewsMeasureSpecs(int widthMeasureSpec) {
        int childWidthMeasureSpec;
        int childHeightMeasureSpec;
        final int parentWidth = MeasureSpec.getSize(widthMeasureSpec)
                                    - getPaddingLeft()
                                    - getPaddingRight();
        int viewWidth;
        int viewHeight;
        int minimumViewHeight = 0;
        for (int index = FIRST_VIEW; index < getChildCount(); index++) {
            final View childView = getChildAt(index);
            measureChildView(childView);
            viewWidth = calculateViewWidth(parentWidth, index);
            viewHeight = childView.getMeasuredHeight();
            childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY);
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY);
            childView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            if (minimumViewHeight == 0) {
                minimumViewHeight = viewHeight;
            }
            minimumViewHeight = Math.min(minimumViewHeight, viewHeight);
        }
        maximumOffsetTopBottom = (int)(minimumViewHeight * MAXIMUM_OFFSET_TOP_BOTTOM_FACTOR);
    }

    private int calculateViewWidth(float parentWidth, int zIndex) {
        float widthMinimumOffsetFactor = getVerticalOffsetFactor();
        float widthMinimumOffset = dp2px(MINIMUM_LEFT_RIGHT_OFFSET_DP);
              widthMinimumOffset -= widthMinimumOffset * widthMinimumOffsetFactor;
        float viewWidth = (parentWidth - (widthMinimumOffset * ((getChildCount() - 1) - zIndex)));
        return (int)viewWidth;
    }

    private float getVerticalOffsetFactor() {
        float result = 0f;
        if (Math.abs(offsetTopBottom) > 0) {
            result = (float)offsetTopBottom / (float)maximumOffsetTopBottom;
        }
        return result;
    }

    private void measureChildView(View view) {
        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                     MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    private void attachChildViews() {
        for (int position = FIRST_VIEW; position < adapter.getCount(); position++) {
            if (getChildCount() < MAXIMUM_ITEMS_ON_SCREEN) {
                viewsBuffer[position] = adapter.getView(position, viewsBuffer[position], this);
                addViewInLayout(viewsBuffer[position], FIRST_VIEW,
                                    viewsBuffer[position].getLayoutParams());
            }
        }
    }

    void collapseVerticalOffset() {
        if (offsetTopBottom > 0) {
            ValueAnimator animator = ValueAnimator.ofInt(offsetTopBottom, INITIAL_OFFSET_IN_PX);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(ANIMATION_DURATION_IN_MS);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    offsetTopBottom = (int) animation.getAnimatedValue();
                    requestLayout();
                }
            });
            animator.start();
        }
    }

    void setOffsetTopBottom(int offset) {
        if (offset >= 0) {
            if (offset > maximumOffsetTopBottom) {
                offsetTopBottom = maximumOffsetTopBottom;
            } else {
                offsetTopBottom = offset;
            }
            requestLayout();
        }
    }

    @Nullable
    View getFirstView() {
        if (getChildCount() > 0) {
            return getChildAt(getChildCount() - 1);
        } else {
            return null;
        }
    }

    private int dp2px(int value) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getContext().getResources().getDisplayMetrics());
    }
}