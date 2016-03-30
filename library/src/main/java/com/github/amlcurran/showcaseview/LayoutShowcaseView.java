package com.github.amlcurran.showcaseview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.amlcurran.showcaseview.targets.Target;

public class LayoutShowcaseView extends RelativeLayout implements View.OnTouchListener, ShowcaseViewApi {

    private final AnimationFactory animationFactory;
    private final ShotStateStore shotStateStore;
    private boolean hasNoTarget = false;

    // Touch items
    private boolean hasCustomClickListener = false; // TODO: needed?
    private boolean blockTouches = true;
    private boolean hideOnTouch = false;
    private OnShowcaseEventListener mEventListener = OnShowcaseEventListener.NONE;

    // Showcase metrics
    private int showcaseX = -1;
    private int showcaseY = -1;
    private float scaleMultiplier = 1f;
    private int showcaseTargetRadius;

    // Animation items
    private long fadeInMillis;
    private long fadeOutMillis;
    private boolean isShowing;
    private boolean blockAllTouches;
    private int contextTextColor;
    private int contentTitleColor;
    private int showcaseBackgroundColor;
    private int layoutResourceId;
    private final int[] positionInWindow = new int[2];

    // Showcase widgets
    private TextView showcaseTitle;
    private TextView showcaseText;
    private Button endButton;
    private Paint mEraserPaint;
    private Bitmap bitmapBuffer;

    public LayoutShowcaseView(Context context) {

        this(context, null);
    }

    public LayoutShowcaseView(final Context context, final AttributeSet attrs) {

        super(context, attrs);
        ApiUtils apiUtils = new ApiUtils();
        if (apiUtils.isCompatWithHoneycomb()) {
            animationFactory = new AnimatorAnimationFactory();
        } else {
            animationFactory = new NoAnimationFactory();
        }
        shotStateStore = new ShotStateStore(context);

        // Set the default animation times
        fadeInMillis = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        fadeOutMillis = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        showcaseBackgroundColor = Color.TRANSPARENT;

        setWillNotDraw(false);
        init();

        showcaseTargetRadius = 32; // TODO - change to
    }

    private void init() {

        setOnTouchListener(this);

        inflate(getContext(), R.layout.showcase_layout, this);
        showcaseTitle = (TextView) findViewById(R.id.showcase_title);
        showcaseText = (TextView) findViewById(R.id.showcase_text);

        endButton = (Button) findViewById(R.id.showcase_button);
        endButton.setOnClickListener(hideOnClickListener);

        mEraserPaint = new Paint();
        mEraserPaint.setColor(0x000000);
        mEraserPaint.setAlpha(0);
        mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        mEraserPaint.setAntiAlias(true);
    }

    private boolean hasShot() {

        return shotStateStore.hasShot();
    }

    void setShowcasePosition(Point point) {

        setShowcasePosition(point.x, point.y);
    }

    void setShowcasePosition(int x, int y) {

        if (shotStateStore.hasShot()) {
            return;
        }
        getLocationInWindow(positionInWindow);
        showcaseX = x - positionInWindow[0];
        showcaseY = y - positionInWindow[1];

        invalidate();
    }

    public void setTarget(final Target target) {

        setShowcase(target, false);
    }

    public void setShowcase(final Target target, final boolean animate) {

        postDelayed(new Runnable() {

            @Override
            public void run() {

                if (!shotStateStore.hasShot()) {

                    if (canUpdateBitmap()) {
                        updateBitmap();
                    }

                    Point targetPoint = target.getPoint();
                    if (targetPoint != null) {
                        hasNoTarget = false;
                        if (animate) {
                            animationFactory.animateTargetToPoint(LayoutShowcaseView.this, targetPoint);
                        } else {
                            setShowcasePosition(targetPoint);
                        }
                    } else {
                        hasNoTarget = true;
                        invalidate();
                    }

                }
            }
        }, 100);
    }

    private void updateBitmap() {

        if (bitmapBuffer == null || haveBoundsChanged()) {
            if (bitmapBuffer != null) {
                bitmapBuffer.recycle();
            }
            bitmapBuffer = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        }
    }

    private boolean haveBoundsChanged() {

        return getMeasuredWidth() != bitmapBuffer.getWidth() ||
                getMeasuredHeight() != bitmapBuffer.getHeight();
    }

    public void setShowcaseX(int x) {

        setShowcasePosition(x, showcaseY);
    }

    public void setShowcaseY(int y) {

        setShowcasePosition(showcaseX, y);
    }

    public int getShowcaseX() {

        return showcaseX;
    }

    public int getShowcaseY() {

        return showcaseY;
    }

    private void overrideButtonClick(OnClickListener listener) {

        if (shotStateStore.hasShot()) {
            return;
        }
        if (endButton != null) {
            if (listener != null) {
                endButton.setOnClickListener(listener);
            } else {
                endButton.setOnClickListener(hideOnClickListener);
            }
        }
        // TODO - need this?
        //hasCustomClickListener = true;
    }

    public void setOnShowcaseEventListener(OnShowcaseEventListener onShowcaseEventListener) {

        mEventListener = onShowcaseEventListener;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    protected void dispatchDraw(Canvas canvas) {

        if (showcaseX < 0 || showcaseY < 0 || shotStateStore.hasShot()) {
            super.dispatchDraw(canvas);
            return;
        }

        //Draw background color
        bitmapBuffer.eraseColor(showcaseBackgroundColor);

        // Draw the showcase drawable
        if (!hasNoTarget) {
            Canvas bufferCanvas = new Canvas(bitmapBuffer);
            bufferCanvas.drawCircle(showcaseX, showcaseY, showcaseTargetRadius, mEraserPaint);

            canvas.drawBitmap(bitmapBuffer, 0, 0, new Paint());
        }

        super.dispatchDraw(canvas);
    }

    @Override
    public void hide() {

        shotStateStore.storeShot();
        mEventListener.onShowcaseViewHide(null);
        fadeOutShowcase();

        // TODO - Cleanup
        endButton.setOnClickListener(null);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        if (blockAllTouches) {
            mEventListener.onShowcaseViewTouchBlocked(motionEvent);
            return true;
        }

        float xDelta = Math.abs(motionEvent.getRawX() - showcaseX);
        float yDelta = Math.abs(motionEvent.getRawY() - showcaseY);
        double distanceFromFocus = Math.sqrt(Math.pow(xDelta, 2) + Math.pow(yDelta, 2));

        if (MotionEvent.ACTION_UP == motionEvent.getAction() &&
                hideOnTouch && distanceFromFocus > getShowcaseTargetRadius()) {
            this.hide();
            return true;
        }

        boolean blocked = blockTouches && distanceFromFocus > getShowcaseTargetRadius();
        if (blocked) {
            mEventListener.onShowcaseViewTouchBlocked(motionEvent);
        }
        return blocked;
    }

    @Override
    public void show() {

        if (canUpdateBitmap()) {
            updateBitmap();
        }
    }

    private void fadeOutShowcase() {

        animationFactory.fadeOutView(
                this, fadeOutMillis, new AnimationFactory.AnimationEndListener() {

                    @Override
                    public void onAnimationEnd() {

                        setVisibility(View.GONE);
                        isShowing = false;
                        mEventListener.onShowcaseViewDidHide(null);

                        // TODO - Cleanup
                        if (bitmapBuffer != null) {
                            bitmapBuffer.recycle();
                            bitmapBuffer = null;
                        }

                        // TODO - Fix for OutOfMemoryErrors
                        if (getParent() != null) {
                            ((ViewGroup) getParent()).removeView(LayoutShowcaseView.this);
                        }
                    }
                }
        );
    }

    @Override
    public void setContentTitle(CharSequence title) {

        showcaseTitle.setText(title);
    }

    @Override
    public void setContentText(CharSequence text) {

        showcaseText.setText(text);
    }

    @Override
    public void setButtonPosition(LayoutParams layoutParams) {

    }

    @Override
    public void setHideOnTouchOutside(boolean hideOnTouch) {

        this.hideOnTouch = hideOnTouch;
    }

    @Override
    public void setBlocksTouches(boolean blockTouches) {

        this.blockTouches = blockTouches;
    }

    private boolean canUpdateBitmap() {

        return getMeasuredHeight() > 0 && getMeasuredWidth() > 0;
    }

    @Override
    public void setStyle(int theme) {

    }

    @Override
    public boolean isShowing() {

        return false;
    }

    public float getShowcaseTargetRadius() {

        return showcaseTargetRadius;
    }

    public void setShowcaseTargetRadius(int showcaseTargetRadius) {

        this.showcaseTargetRadius = showcaseTargetRadius;
    }

    public void setContentTextColor(int contextTextColor) {

        this.contextTextColor = contextTextColor;
    }

    public void setBlockAllTouches(boolean blockAllTouches) {

        this.blockAllTouches = blockAllTouches;
    }

    public void setEndButton(Button endButton) {

        this.endButton.setOnClickListener(null);
        removeView(this.endButton);
        this.endButton = endButton;
        this.endButton.setOnClickListener(hideOnClickListener);
    }

    public void setContentTitleColor(int contentTitleColor) {

        this.contentTitleColor = contentTitleColor;
    }

    public void setShowcaseBackgroundColor(int backgroundColor) {

        this.showcaseBackgroundColor = backgroundColor;
    }

    public void setSingleShot(long shotId) {

        shotStateStore.setSingleShot(shotId);
    }

    public void setLayoutResourceId(int layoutResourceId) {

        this.layoutResourceId = layoutResourceId;

        View view = LayoutInflater.from(getContext()).inflate(layoutResourceId, this, false);
        LayoutParams copyParams = (LayoutParams) view.getLayoutParams();
        endButton.setVisibility(View.GONE);
        showcaseTitle.setVisibility(View.GONE);
        showcaseText.setVisibility(View.GONE);
        // TODO - removeView does not seem to work to remove all views
        removeView(endButton);
        removeView(showcaseTitle);
        removeView(showcaseText);
        copyParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        addView(view, copyParams);
    }

    private static void insertShowcaseView(LayoutShowcaseView showcaseView, ViewGroup parent, int parentIndex) {

        parent.addView(showcaseView, parentIndex);
        if (!showcaseView.hasShot()) {
            showcaseView.show();
        } else {
            showcaseView.hideImmediate();
        }
    }

    private void hideImmediate() {

        isShowing = false;
        setVisibility(GONE);
    }

    /**
     * Builder class which allows easier creation of {@link ShowcaseView}s.
     * It is recommended that you use this Builder class.
     */
    public static class Builder {

        private final LayoutShowcaseView showcaseView;
        private final Activity activity;

        private ViewGroup parent;
        private int parentIndex;

        public Builder(Activity activity) {

            this.activity = activity;
            this.showcaseView = new LayoutShowcaseView(activity);
            this.showcaseView.setTarget(Target.NONE);
            this.parent = (ViewGroup) activity.findViewById(android.R.id.content);
            this.parentIndex = parent.getChildCount();
        }

        /**
         * Create the {@link com.github.amlcurran.showcaseview.ShowcaseView} and show it.
         *
         * @return the created ShowcaseView
         */
        public LayoutShowcaseView build() {

            insertShowcaseView(showcaseView, parent, parentIndex);
            return showcaseView;
        }

        public Builder setLayoutResourceId(int resId) {

            showcaseView.setLayoutResourceId(resId);
            return this;
        }

        /**
         * Set the title text shown on the ShowcaseView.
         */
        public Builder setContentTitle(int resId) {

            return setContentTitle(activity.getString(resId));
        }

        /**
         * Set the title text shown on the ShowcaseView.
         */
        public Builder setContentTitle(CharSequence title) {

            showcaseView.setContentTitle(title);
            return this;
        }

        /**
         * Set the descriptive text shown on the ShowcaseView.
         */
        public Builder setContentText(int resId) {

            return setContentText(activity.getString(resId));
        }

        /**
         * Set the descriptive text shown on the ShowcaseView.
         */
        public Builder setContentText(CharSequence text) {

            showcaseView.setContentText(text);
            return this;
        }

        /**
         * Set the target of the showcase.
         *
         * @param target a {@link com.github.amlcurran.showcaseview.targets.Target} representing
         * the item to showcase (e.g., a button, or action item).
         */
        public Builder setTarget(Target target) {

            showcaseView.setTarget(target);
            return this;
        }

        /**
         * Set the style of the ShowcaseView. See the sample app for example styles.
         */
        public Builder setStyle(int theme) {

            showcaseView.setStyle(theme);
            return this;
        }

        /**
         * Set a listener which will override the button clicks.
         * <p/>
         * Note that you will have to manually hide the ShowcaseView
         */
        public Builder setOnClickListener(OnClickListener onClickListener) {

            showcaseView.overrideButtonClick(onClickListener);
            return this;
        }

        /**
         * Don't make the ShowcaseView block touches on itself. This doesn't
         * block touches in the showcased area.
         * <p/>
         * By default, the ShowcaseView does block touches
         */
        public Builder doNotBlockTouches() {

            showcaseView.setBlocksTouches(false);
            return this;
        }

        /**
         * Make this ShowcaseView hide when the user touches outside the showcased area.
         * This enables {@link #doNotBlockTouches()} as well.
         * <p/>
         * By default, the ShowcaseView doesn't hide on touch.
         */
        public Builder hideOnTouchOutside() {

            showcaseView.setBlocksTouches(true);
            showcaseView.setHideOnTouchOutside(true);
            return this;
        }

        /**
         * Set the ShowcaseView to only ever show once.
         *
         * @param shotId a unique identifier (<em>across the app</em>) to store
         * whether this ShowcaseView has been shown.
         */
        public Builder singleShot(long shotId) {

            showcaseView.setSingleShot(shotId);
            return this;
        }

        public Builder setShowcaseEventListener(OnShowcaseEventListener showcaseEventListener) {

            showcaseView.setOnShowcaseEventListener(showcaseEventListener);
            return this;
        }

        public Builder setParent(ViewGroup parent, int index) {

            this.parent = parent;
            this.parentIndex = index;
            return this;
        }

        /**
         * Sets the color that will draw the text as specified by {@link #setContentText(CharSequence)}
         * or {@link #setContentText(int)}.
         */
        public Builder setContentTextColor(int textColor) {

            showcaseView.setContentTextColor(textColor);
            return this;
        }

        /**
         * Sets the Color that will draw the text as specified by {@link #setContentTitle(CharSequence)}
         * or {@link #setContentTitle(int)}.
         */
        public Builder setContentTitleColor(int textColor) {

            showcaseView.setContentTitleColor(textColor);
            return this;
        }

        /**
         * Sets the Color (resource id) that will draw background of the showcase
         */
        public Builder setBackgroundColorId(int backgroundColorId) {

            setBackgroundColor(ContextCompat.getColor(showcaseView.getContext(), backgroundColorId));
            return this;
        }

        /**
         * Sets the Color (RGBA hex value) that will draw background of the showcase
         */
        public Builder setBackgroundColor(int backgroundColor) {

            showcaseView.setShowcaseBackgroundColor(backgroundColor);
            return this;
        }

        /**
         * Replace the end button with the one provided. Note that this resets any OnClickListener provided
         * by {@link #setOnClickListener(OnClickListener)}, so call this method before that one.
         */
        private Builder setEndButtonId(Button button) {

            showcaseView.setEndButton(button);
            return this;
        }

        /**
         * Replace the end button with the one provided. Note that this resets any OnClickListener provided
         * by {@link #setOnClickListener(OnClickListener)}, so call this method before that one.
         */
        public Builder setEndButtonId(int buttonResourceId) {

            View view = showcaseView.findViewById(buttonResourceId);
            if (!(view instanceof Button)) {
                throw new IllegalArgumentException("Attempted to replace showcase button with a layout which isn't a button");
            }

            return setEndButtonId((Button) view);
        }

        /**
         * Block any touch made on the ShowcaseView, even inside the showcase
         */
        public Builder blockAllTouches() {

            showcaseView.setBlockAllTouches(true);
            return this;
        }

        /**
         * Uses the android decor view to insert a showcase, this is not recommended
         * as then UI elements in showcase view can hide behind the nav bar
         */
        public Builder useDecorViewAsParent() {

            this.parent = ((ViewGroup) activity.getWindow().getDecorView());
            this.parentIndex = -1;
            return this;
        }

        /**
         * Specifies the radius of the showcase target to be drawn
         */
        public Builder setShowcaseTargetRadius(int showcaseTargetRadius) {

            showcaseView.setShowcaseTargetRadius(showcaseTargetRadius);
            return this;
        }

    }

    private OnClickListener hideOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {

            hide();
        }
    };

}
