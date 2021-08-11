package lhg.drawerlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.OverScroller;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * DrawerLayout: has no padding
 */
public class DrawerLayout extends ViewGroup {
    private static final String TAG = "DrawerLayout";
    private static final int DEFAULT_SCRIM_COLOR = 0x99000000;

    private static int MinVelSettle = 1000;//松手不归位的最小速度
    private int mScrimColor = DEFAULT_SCRIM_COLOR;
    private Paint mScrimPaint = new Paint();

    private DrawerListener mDrawerListener;

    private final DrawerDeledges mDrawerDeledges = new DrawerDeledges();
    private ViewScrollHelper mScrollHelper;
    private DrawerLayoutScrollCallback mScrollCallback;
    private OverScroller mScroller;
    private StateChangedRunnable stateChangedRunnable = new StateChangedRunnable();
    private final CheckClickHelper checkClickHelper = new CheckClickHelper();

    public DrawerLayout(Context context) {
        this(context, null);
    }

    public DrawerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public DrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DrawerLayout);
            this.mStatusBarBackground = a.getDrawable(R.styleable.DrawerLayout_dlt_statusBarBackground);
            a.recycle();
        }

        initApplyInsets(context);

        mScrollHelper = new DrawerLayoutScrollHerlper(getContext(), this, mScrollCallback = new DrawerLayoutScrollCallback(), mDrawerDeledges);
        mScroller = mScrollHelper.getScroller();
        mDrawerDeledges.init(this, mScrollHelper);
        initCheckClickHelper(mScrollHelper.mTouchSlop);
    }

    private void initCheckClickHelper(int touchSlop) {
        checkClickHelper.init(this, touchSlop, v -> {
            Log.i(TAG, "initCheckClickHelper " + isContentView(v));
            DrawerDeledge deledge = mDrawerDeledges.ofOpened();
            if (isContentView(v)) {
                if (deledge != null) {
                    deledge.settleLayout(false);
                } else if (mScrollHelper.isDragging()) {
                    mScrollHelper.setIsDragging(false);
                    if (mScrollCallback.deledge != null) {
                        mScrollCallback.deledge.settleLayout(false);
                    }
                }
            }
        });
    }

    private void initApplyInsets(Context context) {
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (ViewCompat.getFitsSystemWindows(this)) {
            if (Build.VERSION.SDK_INT >= 21) {
                ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
                    setChildInsets(insets, insets.getSystemWindowInsetTop() > 0);
                    return insets.consumeSystemWindowInsets();
                });

                if (mStatusBarBackground == null) {
                    final TypedArray a = context.obtainStyledAttributes(THEME_ATTRS);
                    try {
                        mStatusBarBackground = a.getDrawable(0);
                    } finally {
                        a.recycle();
                    }
                }
                setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            } else {
                mStatusBarBackground = null;
            }
        }

    }

    public DrawerListener getPullDownListener() {
        return mDrawerListener;
    }

    public void setPullDownListener(DrawerListener drawerListener) {
        this.mDrawerListener = drawerListener;
    }

    public void lockDrawer(int gravity, boolean lock) {
        DrawerDeledge deledge = mDrawerDeledges.ofGravity(gravity);
        if (deledge != null) {
            deledge.drawerLocked = lock;
        }
    }

    public boolean isDrawerLocked(int gravity) {
        DrawerDeledge deledge = mDrawerDeledges.ofGravity(gravity);
        if (deledge != null) {
            return deledge.drawerLocked;
        }
        return false;
    }

    public boolean isOpen() {
        return getOpenedDrawerView() != null;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        if (disallowIntercept) {
            close();
        }
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean ret = mScrollHelper.interceptTouchEvent(ev);
        if (!ret) {
            DrawerDeledge deledge = mDrawerDeledges.ofOpened();
            ret = deledge!= null && ViewScrollHelper.findTopChildUnder(this, ev.getRawX(), ev.getRawY()) == deledge.bodyView;
        }
        checkClickHelper.onEvent(ev);
        return ret;
    }


    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean ret = mScrollHelper.onTouchEvent(ev);
        checkClickHelper.onEvent(ev);
        return ret;
    }

    private static class CheckClickHelper {
        boolean hasMoved;
        private float initMotionX;
        private float initMotionY;
        private ViewGroup rootView;
        private int touchSlop;
        private OnClickListener onClickListener;

        private void init(ViewGroup rootView, int touchSlop, OnClickListener onClickListener) {
            this.rootView = rootView;
            this.touchSlop = touchSlop;
            this.onClickListener = onClickListener;
        }

        void onEvent(MotionEvent ev) {
            final int actionMasked = ev.getActionMasked();
            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN:
                    hasMoved = false;
                    initMotionX = ev.getRawX();
                    initMotionY = ev.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (hasMoved) {
                        return ;
                    }
                    float dx = ev.getRawX() - initMotionX;
                    float dy = ev.getRawY() - initMotionY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        hasMoved = true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    if (!hasMoved) {
                        if (onClickListener != null) {
                            onClickListener.onClick(ViewScrollHelper.findTopChildUnder(rootView, ev.getRawX(), ev.getRawY()));
                        }
                        return;
                    }
                    break;
                }
            }
        }

    }

    private static void stopChildScroll(View root) {
        ViewCompat.stopNestedScroll(root, ViewCompat.TYPE_NON_TOUCH);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                stopChildScroll(group.getChildAt(i));
            }
        }
    }

    private static boolean isDrawerOpen(View v) {
        return ((LayoutParams) v.getLayoutParams()).isOpen;
    }



    public void close() {
        DrawerDeledge d = mDrawerDeledges.ofOpened();
        if (d != null && mScrollCallback.deledge == null) {
            mScrollCallback.deledge = d;
        }
        if (mScrollCallback.deledge != null) {
            mScrollCallback.deledge.settleLayout(false);
        }
    }

    public void open(int gravity) {
        DrawerDeledge d = mDrawerDeledges.ofGravity(gravity);
        if (d != null && d.drawerView != null) {
            mScrollCallback.deledge = d;
            d.settleLayout(true);
        }
    }
    

    private class StateChangedRunnable implements Runnable {
        View drawerView;
        int gravity;
        boolean isOpen;
        @Override
        public void run() {
            postStateChanged(drawerView, isOpen, gravity);
        }
    }

    private void postStateChanged(View drawerView, boolean open, int gravity) {
        if (mDrawerListener != null) {
            if (open) {
                mDrawerListener.onDrawerOpened(this, drawerView, gravity);
            } else {
                mDrawerListener.onDrawerClosed(this, drawerView, gravity);
            }
        }
    }

    @Override
    public void onViewAdded(View child) {
        if (getChildCount() > 5) {
            throw new IllegalStateException("most child count = 5");
        }
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.gravity == Gravity.NO_GRAVITY) {
            for (DrawerDeledge c : mDrawerDeledges.all) {
                c.bodyView = child;
                c.decideWhoIsFront();
            }
        }
        final int horizontalGravity = Gravity.getAbsoluteGravity(lp.gravity, ViewCompat.getLayoutDirection(this)) & Gravity.HORIZONTAL_GRAVITY_MASK;
        final int verticalGravity = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
        for (DrawerDeledge c : mDrawerDeledges.all) {
            if (c.gravity == horizontalGravity || c.gravity == verticalGravity) {
                c.drawerView = child;
                c.decideWhoIsFront();
                break;
            }
        }
        super.onViewAdded(child);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        for (DrawerDeledge c : mDrawerDeledges.all) {
            if (c.bodyView == child) {
                c.bodyView = null;
            }
            if (c.drawerView == child) {
                c.drawerView = null;
            }
        }
    }


    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            DrawerDeledge deledge = (DrawerDeledge) mScrollCallback.deledge;
            if (deledge != null) {
                deledge.scrollBodyBy(mScroller.getCurrX() - deledge.getDrawerVisibleSize());
            }
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean hasLayoutBody = false;
        for (DrawerDeledge d : mDrawerDeledges.all) {
            if (d.drawerView != null) {
                d.layoutDrawerView();
                if (d.isDrawerOpen()) {
                    d.layoutBodyView();
                    hasLayoutBody = true;
                }
            }
        }
        if (!hasLayoutBody) {
            mDrawerDeledges.left().layoutBodyView();
        }
    }

    public interface DrawerListener {
        void onDrawerOpened(@NonNull DrawerLayout v, @NonNull View drawerView, int gravity);

        void onDrawerClosed(@NonNull DrawerLayout v, @NonNull View drawerView, int gravity);
    }


    private static final int[] THEME_ATTRS = {
            android.R.attr.colorPrimaryDark
    };

    private Drawable mStatusBarBackground;
    private Object mInsets;
    private boolean mDrawStatusBarBackground;



    public void setChildInsets(Object insets, boolean draw) {
        this.mInsets = insets;
        mDrawStatusBarBackground = draw;
        setWillNotDraw(!draw && getBackground() == null);
        requestLayout();
    }

    boolean isContentView(View child) {
        return child.getParent() == this && ((LayoutParams) child.getLayoutParams()).gravity == Gravity.NO_GRAVITY;
    }

    boolean isDrawerView(View child) {
        return child.getParent() == this && ((LayoutParams) child.getLayoutParams()).gravity != Gravity.NO_GRAVITY;
    }

    private void applyWindowInsetsToChild(View child, WindowInsetsCompat wi) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (ViewCompat.getFitsSystemWindows(child)) {
            ViewCompat.dispatchApplyWindowInsets(child, wi);
        } else if (lp.applyWindowInsets == LayoutParams.ApplyWindowInsets_Margin) {
            lp.setMargins(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
        } else if (lp.applyWindowInsets == LayoutParams.ApplyWindowInsets_Padding) {
            child.setPadding(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalArgumentException("DrawerLayout must be measured with MeasureSpec.EXACTLY.");
        }

        setMeasuredDimension(widthSize, heightSize);

        final int childCount = getChildCount();
        final boolean applyInsets = mInsets != null && ViewCompat.getFitsSystemWindows(this);

        //measure content
        int contentSize = 0;
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (isContentView(child)) {
                if (applyInsets) {
                    applyWindowInsetsToChild(child, (WindowInsetsCompat) mInsets);
                }
                // Content views get measured at exactly the layout's size.
                final int contentWidthSpec = MeasureSpec.makeMeasureSpec(
                        widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                final int contentHeightSpec = MeasureSpec.makeMeasureSpec(
                        heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
                contentSize++;
            }
        }
        if (contentSize != 1) {
            throw new IllegalArgumentException("DrawerLayout must only has one contentView " + contentSize);
        }

        //measure drawer
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (!isDrawerView(child)) {
                continue;
            }

            int hMargin = lp.leftMargin + lp.rightMargin;
            int vMargin = lp.topMargin + lp.bottomMargin;
            int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec, hMargin, lp.width);
            int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec, vMargin, lp.height);

            final int horizontalGravity = Gravity.getAbsoluteGravity(lp.gravity, layoutDirection) & Gravity.HORIZONTAL_GRAVITY_MASK;
            final int verticalGravity = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
            boolean isLeftEdgeDrawer = (horizontalGravity == Gravity.LEFT);
            boolean isRightEdgeDrawer = (horizontalGravity == Gravity.RIGHT);
            boolean isTopEdgeDrawer = (verticalGravity == Gravity.TOP);
            boolean isBottomEdgeDrawer = (verticalGravity == Gravity.BOTTOM);


            //apply insets
            if (applyInsets && Build.VERSION.SDK_INT >= 20) {
                WindowInsetsCompat wi = (WindowInsetsCompat) mInsets;
                if (isLeftEdgeDrawer) {
                    wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(),
                            0, wi.getSystemWindowInsetBottom());
                }
                if (isRightEdgeDrawer) {
                    wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(),
                            wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
                }
                if (isTopEdgeDrawer) {
                    wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(),
                            wi.getSystemWindowInsetRight(), 0);
                }
                if (isBottomEdgeDrawer) {
                    wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), 0,
                            wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
                }
                applyWindowInsetsToChild(child, wi);
            }


            if (isLeftEdgeDrawer || isRightEdgeDrawer) {
                if (lp.sizeWeight > 0.01) {
                    drawerWidthSpec = MeasureSpec.makeMeasureSpec(Math.min((int) (widthSize * lp.sizeWeight), widthSize - hMargin), MeasureSpec.EXACTLY);
                }
                drawerHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - vMargin, MeasureSpec.EXACTLY);
            }
            if (isTopEdgeDrawer || isBottomEdgeDrawer) {
                if (lp.sizeWeight > 0.01) {
                    drawerHeightSpec = MeasureSpec.makeMeasureSpec(Math.min((int) (heightSize * lp.sizeWeight), heightSize - vMargin), MeasureSpec.EXACTLY);
                }
                drawerWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - hMargin, MeasureSpec.EXACTLY);
            }
            child.measure(drawerWidthSpec, drawerHeightSpec);
        }
    }

    int getDrawerViewAbsoluteGravity(View drawerView) {
        final int gravity = ((LayoutParams) drawerView.getLayoutParams()).gravity;
        return GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
    }

    public void setStatusBarBackground(@Nullable Drawable bg) {
        mStatusBarBackground = bg;
        invalidate();
    }

    @Nullable
    public Drawable getStatusBarBackgroundDrawable() {
        return mStatusBarBackground;
    }


    public void setStatusBarBackground(int resId) {
        mStatusBarBackground = resId != 0 ? ContextCompat.getDrawable(getContext(), resId) : null;
        invalidate();
    }


    public void setStatusBarBackgroundColor(@ColorInt int color) {
        mStatusBarBackground = new ColorDrawable(color);
        invalidate();
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        if (mDrawStatusBarBackground && mStatusBarBackground != null) {
            final int inset;
            if (Build.VERSION.SDK_INT >= 21) {
                inset = mInsets != null ? ((WindowInsetsCompat) mInsets).getSystemWindowInsetTop() : 0;
            } else {
                inset = 0;
            }
            if (inset > 0) {
//                int childCount = getChildCount();
//                boolean hasMargin = false;
//                for (int i = 0; i < childCount; i++) {
//                    final View child = getChildAt(i);
//                    if (child.getVisibility() == GONE) {
//                        continue;
//                    }
//                    LayoutParams lp = (LayoutParams) child.getLayoutParams();
//                    if (lp.applyWindowInsets == LayoutParams.ApplyWindowInsets_Margin) {
//                        hasMargin = true;
//                        break;
//                    }
//                }
//                if (hasMargin) {
                mStatusBarBackground.setBounds(0, 0, getWidth(), inset);
                mStatusBarBackground.draw(c);
//                }
            }
        }
    }

    final RectF bodyRect = new RectF();
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean ret = super.drawChild(canvas, child, drawingTime);
        DrawerDeledge deledge = (DrawerDeledge) mScrollCallback.deledge;
        if (deledge == null) {
            deledge = mDrawerDeledges.ofOpened();
        }
        if (deledge == null) {
            return ret;
        }
        if (isContentView(child)) {
            deledge.getBodyRect(bodyRect);
            final float scrimOpacity = deledge.getDrawerVisibleSize()*1f / deledge.drawerSize();
            final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
            final int imag = (int) (baseAlpha * scrimOpacity);
            final int color = imag << 24 | (mScrimColor & 0xffffff);
            mScrimPaint.setColor(color);
            canvas.drawRect(bodyRect, mScrimPaint);
        }
        return ret;
    }

    //////////////////////////////////////////////////////////////

    private class DrawerLayoutScrollCallback implements ViewScrollHelper.Callback {
        DrawerDeledge deledge = null;
        @Override
        public int getScrollDirection() {
            return deledge == null ? ViewScrollHelper.DIRECTION_NONE : deledge.getScrollDirection();
        }

        @Override
        public boolean onDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            return deledge == null ? false : deledge.onDragBegin(child, x, y, dx, dy, edge);
        }

        @Override
        public boolean onScroll(int x, int y, int dx, int dy) {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            return deledge == null ? false : deledge.onScroll(x, y, dx, dy);
        }

        @Override
        public void onDragEnd(int xvel, int yvel) {
            if (deledge != null) {
                deledge.onDragEnd(xvel, yvel);
            }
        }
    };

    private static class DrawerLayoutScrollHerlper extends ViewScrollHelper {
        DrawerLayoutScrollCallback scrollCallback;
        DrawerDeledges drawerDeledges;
        public DrawerLayoutScrollHerlper( Context context, ViewGroup view, DrawerLayoutScrollCallback cb, DrawerDeledges drawerDeledges) {
            super(context, view, cb);
            scrollCallback = cb;
            this.drawerDeledges = drawerDeledges;
        }

        @Override
        protected boolean checkTouchSlop(float dx, float dy) {
            scrollCallback.deledge = drawerDeledges.ofOpened();
            if (scrollCallback.deledge == null) {
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > mTouchSlop) {
                    if (dx > 0 && drawerDeledges.left().drawerView != null) {
                        scrollCallback.deledge = drawerDeledges.left();
                    }
                    if (dx < 0 && drawerDeledges.right().drawerView != null) {
                        scrollCallback.deledge = drawerDeledges.right();
                    }
                }
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > mTouchSlop) {
                    if (dy > 0 && drawerDeledges.top().drawerView != null) {
                        scrollCallback.deledge = drawerDeledges.top();
                    }
                    if (dy < 0 && drawerDeledges.bottom().drawerView != null) {
                        scrollCallback.deledge = drawerDeledges.bottom();
                    }
                }
            }
            return super.checkTouchSlop(dx, dy);
        }
    }

    private static abstract class DrawerDeledge implements ViewScrollHelper.Callback {
        public final int gravity;
        DrawerLayout drawerLayout;
        ViewScrollHelper scrollHelper;
        View drawerView, bodyView;
        boolean drawerViewIsFront = true;
        boolean drawerLocked = false;//true 禁止打开

        protected DrawerDeledge(int gravity) {
            this.gravity = gravity;
        }

        void init(DrawerLayout drawerLayout, ViewScrollHelper scrollHelper) {
            this.drawerLayout = drawerLayout;
            this.scrollHelper = scrollHelper;
        }

        void decideWhoIsFront() {
            if (drawerView == null || bodyView == null) {
                return;
            }
            drawerViewIsFront = (drawerLayout.indexOfChild(drawerView) > drawerLayout.indexOfChild(bodyView));
        }

        boolean canViewScroll(View view, int x, int y, int dx, int dy) {
            return ViewScrollHelper.canScroll(view, true, dx, dy, x - view.getLeft() + view.getScrollX(), y - view.getTop() + view.getScrollY());
        }

        abstract int drawerSize();

        //永远>=0
        abstract int getDrawerVisibleSize();

        boolean isDrawerOpen() {
            return drawerView != null && ((LayoutParams) drawerView.getLayoutParams()).isOpen;
        }

        @Override
        public boolean shouldDragIfWrongDirectionScrollFirst() {
            return isDrawerOpen();//open的时候需要拦截
        }

        boolean drawerShouldScroll() {
            if (drawerView == null || bodyView == null) {
                return false;
            }
            if (((LayoutParams)drawerView.getLayoutParams()).mode == LayoutParams.Mode_Concat) {
                return true;
            }
            return drawerViewIsFront;
        }

        boolean bodyShouldScroll() {
            if (((LayoutParams)drawerView.getLayoutParams()).mode == LayoutParams.Mode_Concat) {
                return true;
            }
            return !drawerViewIsFront;
        }

        @Override
        public boolean onDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            if (drawerView == null) {
                return false;
            }
            if (drawerLocked) {
                return false;//禁止拖动
            }
            boolean drag = shouldDragBegin(child, x, y, dx, dy, edge);
            if (drag) {
                stopChildScroll(bodyView);
            }
            return drag;
        }

        public abstract boolean shouldDragBegin(View child, int x, int y, int dx, int dy, int edge);

        @Override
        public boolean onScroll(int x, int y, int dx, int dy) {
            //手指从上往下dy>0

            scrollBodyBy(xyToDistance(dx ,dy));
            drawerLayout.invalidate();
            return true;
        }
        
        abstract int xyToDistance(int dx, int dy);

        @Override
        public void onDragEnd(int xvel, int yvel) {
            Log.i(TAG, "onDragEnd ");
            LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
            if (lp.closeType == LayoutParams.CloseType_Allways) {
                settleLayout(false);
                return;
            }
            Boolean menuIsOpen = shouldOpenOnRelease(xvel, yvel);
            if (menuIsOpen == null) {
                if (isDrawerOpen()) {
                    menuIsOpen = getDrawerVisibleSize() > drawerSize() * lp.pullToClosePosition;
                } else {
                    menuIsOpen = getDrawerVisibleSize() > drawerSize() * lp.pullToOpenPosition;
                }
            }
            settleLayout(menuIsOpen);
        }

        protected abstract Boolean shouldOpenOnRelease(int xvel, int yvel);

        abstract int drawerMarginEdge();


        //释放layout归位
        void settleLayout(boolean open) {
            Log.i(TAG, "settleLayout " + open);
            if (!scrollHelper.getScroller().isFinished()) {
                scrollHelper.getScroller().forceFinished(true);
            }
            if (drawerView == null) {
                return;
            }
            boolean stateChanged = (open != isDrawerOpen());
            ((LayoutParams) drawerView.getLayoutParams()).isOpen = open;
            int visibleSize = getDrawerVisibleSize();
            int distance = open ? drawerSize() - visibleSize : -visibleSize;
            int duration = scrollHelper.computeSettleDuration(drawerSize(), 0, distance, 0, 0, 0);
            //无论是x还是y的滚动，都是使用x坐标轴， 反正滚动是距离，在scrollBodyBy中会根据具体坐标轴真实滚动
            scrollHelper.getScroller().startScroll(visibleSize, 0, distance, 0, duration);
            drawerLayout.invalidate();
            if (stateChanged) {
                StateChangedRunnable stateChangedRunnable = drawerLayout.stateChangedRunnable;
                stateChangedRunnable.gravity = gravity;
                stateChangedRunnable.isOpen = open;
                stateChangedRunnable.drawerView = drawerView;
                drawerLayout.post(stateChangedRunnable);
            }
        }

        /**
         * @param distance > 0 : 表示在打开 ， < 0 表示在关闭
         */
        public void scrollBodyBy(int distance) {
            if (drawerView == null) {
                return;
            }
            int minDis = 0;
            int maxDis = drawerSize();

            int oldPos = getDrawerVisibleSize();
            if (distance + oldPos < minDis) {
                distance = minDis - oldPos;
                if (!scrollHelper.getScroller().isFinished()) {
                    scrollHelper.getScroller().abortAnimation();
                }
            } else if (distance + oldPos > maxDis) {
                distance = maxDis - oldPos;
                if (!scrollHelper.getScroller().isFinished()) {
                    scrollHelper.getScroller().abortAnimation();
                }
            }
            if (bodyShouldScroll() && distance != 0) {
                offsetDrawerDistance(bodyView, distance);
            }

            int newPos = Math.max(minDis, Math.min(maxDis, distance + getDrawerVisibleSize()));
            if (drawerShouldScroll()) {
                offsetDrawerDistance(drawerView, newPos - oldPos);
            }
            drawerView.setVisibility(newPos <= minDis ? INVISIBLE : VISIBLE);
        }

        abstract void offsetDrawerDistance(View view, int distance);

        public abstract void layoutDrawerView();
        public abstract void layoutBodyView();

        public abstract void getBodyRect(RectF rect);
    };


    private View getOpenedDrawerView() {
        DrawerDeledge d = mDrawerDeledges.ofOpened();
        return d == null ? null : d.drawerView;
    }

    private static class DrawerDeledgeLeft extends DrawerDeledge {

        DrawerDeledgeLeft() {
            super(Gravity.LEFT);
        }

        @Override
        public int getScrollDirection() {
            return drawerView == null ? ViewScrollHelper.DIRECTION_NONE : ViewScrollHelper.DIRECTION_HORIZONTAL;
        }


        int drawerMarginEdge() {
            return ((LayoutParams)drawerView.getLayoutParams()).leftMargin;
        }

        int getDrawerVisibleSize() {
            if (drawerView == null || bodyView == null) {
                return 0;
            }
            if (drawerShouldScroll()) {
                return (drawerView.getRight()  - drawerMarginEdge());
            } else {
                return (bodyView.getLeft() - ((LayoutParams)bodyView.getLayoutParams()).leftMargin);
            }
        }

        @Override
        int drawerSize() {
            return drawerView == null ? 0 : drawerView.getWidth();
        }

        @Override
        public boolean shouldDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            boolean drag = true;
            boolean isOpen = isDrawerOpen();
            if (drawerLayout.getOpenedDrawerView() == null && (edge & ViewScrollHelper.EDGE_LEFT) == ViewScrollHelper.EDGE_LEFT) {
            } else if (!scrollHelper.getScroller().isFinished()) {
//            } else if (x < mBodyView.getLeft()) {
            } else if (dx > 0 && !isOpen && !canViewScroll(child, x, y, dx, 0)) {
                // 向下滑动
            } else if (dx < 0 && isOpen) {
                //菜单已展开, 且向左滑动了, 拦截
            } else {
                drag = false;
            }
            return drag;
        }

        @Override
        int xyToDistance(int dx, int dy) {
            return dx;
        }

        @Override
        protected Boolean shouldOpenOnRelease(int xvel, int yvel) {
            if (xvel > MinVelSettle) {
                return true;
            } else if (xvel < -MinVelSettle) {
                return false;
            }
            return null;
        }

        @Override
        void offsetDrawerDistance(View view, int distance) {
            view.offsetLeftAndRight(distance);
        }

        @Override
        public void layoutDrawerView() {
            final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
            final int width = drawerView.getMeasuredWidth();
            final int height = drawerView.getMeasuredHeight();
            int left = isDrawerOpen() || !drawerShouldScroll() ? lp.leftMargin : lp.leftMargin - width;
            int top = lp.topMargin;
            drawerView.layout(left, top, left + width, top + height);
        }

        @Override
        public void layoutBodyView() {
            final LayoutParams lp = (LayoutParams) bodyView.getLayoutParams();
            final int width = bodyView.getMeasuredWidth();
            final int height = bodyView.getMeasuredHeight();
            int left = lp.leftMargin;
            int top = lp.topMargin;
            if (isDrawerOpen() && bodyShouldScroll()) {
                left += drawerSize();
            }
            bodyView.layout(left, top, left + width, top + height);
        }

        @Override
        public void getBodyRect(RectF rect) {
            rect.set(drawerView.getRight(), 0, drawerLayout.getWidth(), drawerLayout.getHeight());
        }
    }

    private static class DrawerDeledgeRight extends DrawerDeledge {

        private DrawerDeledgeRight() {
            super(Gravity.RIGHT);
        }

        @Override
        int drawerSize() {
            return drawerView == null ? 0 : drawerView.getWidth();
        }

        @Override
        int getDrawerVisibleSize() {
            if (drawerView == null || bodyView == null) {
                return 0;
            }
            if (drawerShouldScroll()) {
                return drawerLayout.getWidth() - drawerView.getLeft() - drawerMarginEdge();
            } else {
                return drawerLayout.getWidth() - bodyView.getRight() - ((LayoutParams)bodyView.getLayoutParams()).rightMargin;
            }
        }

        @Override
        public int getScrollDirection() {
            return drawerView == null ? ViewScrollHelper.DIRECTION_NONE : ViewScrollHelper.DIRECTION_HORIZONTAL;
        }

        @Override
        public boolean shouldDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            boolean drag = true;
            boolean isOpen = isDrawerOpen();
            if (drawerLayout.getOpenedDrawerView() == null && (edge & ViewScrollHelper.EDGE_RIGHT) == ViewScrollHelper.EDGE_RIGHT) {
            } else if (!scrollHelper.getScroller().isFinished()) {
            } else if (dx < 0 && !isOpen && !canViewScroll(child, x, y, dx, 0)) {
                // 向左边滑动
            } else if (dx > 0 && isOpen) {
                //菜单已展开, 且向右滑动了, 拦截
            } else {
                drag = false;
            }
            return drag;
        }

        @Override
        int xyToDistance(int dx, int dy) {
            return -dx;
        }

        @Override
        protected Boolean shouldOpenOnRelease(int xvel, int yvel) {
            if (xvel < -MinVelSettle) {
                return true;
            } else if (xvel > MinVelSettle) {
                return false;
            }
            return null;
        }

        @Override
        int drawerMarginEdge() {
            return ((LayoutParams)drawerView.getLayoutParams()).rightMargin;
        }

        @Override
        void offsetDrawerDistance(View view, int distance) {
            view.offsetLeftAndRight(-distance);
        }

        @Override
        public void layoutDrawerView() {
            final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
            final int width = drawerView.getMeasuredWidth();
            final int height = drawerView.getMeasuredHeight();
            int left = drawerLayout.getWidth() - lp.rightMargin - (isDrawerOpen() || !drawerShouldScroll() ? width : 0);
            int top = lp.topMargin;
            drawerView.layout(left, top, left + width, top + height);
        }

        @Override
        public void layoutBodyView() {
            final LayoutParams lp = (LayoutParams) bodyView.getLayoutParams();
            final int width = bodyView.getMeasuredWidth();
            final int height = bodyView.getMeasuredHeight();
            int left = lp.leftMargin;
            int top = lp.topMargin;
            if (isDrawerOpen() && bodyShouldScroll()) {
                left -= drawerSize();
            }
            bodyView.layout(left, top, left + width, top + height);
        }

        @Override
        public void getBodyRect(RectF rect) {
            rect.set(0, 0, drawerView.getLeft(), drawerLayout.getHeight());

        }
    }

    private static class DrawerDeledgeTop extends DrawerDeledge {

        private DrawerDeledgeTop() {
            super(Gravity.TOP);
        }
        @Override
        public int getScrollDirection() {
            return drawerView == null ? ViewScrollHelper.DIRECTION_NONE : ViewScrollHelper.DIRECTION_VERTICAL;
        }

        int getDrawerVisibleSize() {
            if (drawerView == null || bodyView == null) {
                return 0;
            }
            if (drawerShouldScroll()) {
                return drawerView.getBottom() - drawerMarginEdge();
            } else {
                return bodyView.getTop() - ((LayoutParams)bodyView.getLayoutParams()).topMargin;
            }
        }

        @Override
        int drawerSize() {
            return drawerView == null ? 0 : drawerView.getHeight();
        }

        @Override
        public boolean shouldDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            boolean drag = true;
            boolean isOpen = isDrawerOpen();
            if (drawerLayout.getOpenedDrawerView() == null && (edge & ViewScrollHelper.EDGE_TOP) == ViewScrollHelper.EDGE_TOP) {
            } else if (!scrollHelper.getScroller().isFinished()) {
            } else if (dy > 0 && !isOpen && !canViewScroll(child, x, y, 0, dy)) {
                // 向下滑动
            } else if (dy < 0 && isOpen) {
                //菜单已展开, 且向上滑动了, 拦截
            } else {
                drag = false;
            }
            return drag;
        }

        @Override
        int xyToDistance(int dx, int dy) {
            return dy;
        }

        @Override
        protected Boolean shouldOpenOnRelease(int xvel, int yvel) {
            if (yvel > MinVelSettle) {
                return true;
            } else if (yvel < -MinVelSettle) {
                return false;
            }
            return null;
        }

        @Override
        int drawerMarginEdge() {
            return ((LayoutParams)drawerView.getLayoutParams()).topMargin;
        }

        @Override
        void offsetDrawerDistance(View view, int distance) {
            view.offsetTopAndBottom(distance);
        }

        @Override
        public void layoutDrawerView() {
            final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
            final int width = drawerView.getMeasuredWidth();
            final int height = drawerView.getMeasuredHeight();
            int top =  lp.topMargin - (isDrawerOpen() || !drawerShouldScroll() ? 0: height);
            int left = lp.leftMargin;
            drawerView.layout(left, top, left + width, top + height);
        }

        @Override
        public void layoutBodyView() {
            final LayoutParams lp = (LayoutParams) bodyView.getLayoutParams();
            final int width = bodyView.getMeasuredWidth();
            final int height = bodyView.getMeasuredHeight();
            int left = lp.leftMargin;
            int top = lp.topMargin;
            if (isDrawerOpen() && bodyShouldScroll()) {
                top += drawerSize();
            }
            bodyView.layout(left, top, left + width, top + height);
        }

        @Override
        public void getBodyRect(RectF rect) {
            rect.set(0, drawerView.getBottom(), drawerLayout.getWidth(), drawerLayout.getHeight());
        }
    }


    private static class DrawerDeledgeBottom extends DrawerDeledge {

        private DrawerDeledgeBottom() {
            super(Gravity.BOTTOM);
        }
        @Override
        public int getScrollDirection() {
            return drawerView == null ? ViewScrollHelper.DIRECTION_NONE : ViewScrollHelper.DIRECTION_VERTICAL;
        }

        int getDrawerVisibleSize() {
            if (drawerView == null || bodyView == null) {
                return 0;
            }
            if (drawerShouldScroll()) {
                return drawerLayout.getHeight() - drawerView.getTop() - drawerMarginEdge();
            } else {
                return drawerLayout.getHeight() - bodyView.getBottom() - ((LayoutParams)bodyView.getLayoutParams()).bottomMargin;
            }
        }

        @Override
        int drawerSize() {
            return drawerView == null ? 0 : drawerView.getHeight();
        }

        @Override
        public boolean shouldDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            boolean drag = true;
            boolean isOpen = isDrawerOpen();
            if (drawerLayout.getOpenedDrawerView() == null && (edge & ViewScrollHelper.EDGE_BOTTOM) == ViewScrollHelper.EDGE_BOTTOM) {
            } else if (!scrollHelper.getScroller().isFinished()) {
            } else if (dy < 0 && !isOpen && !canViewScroll(child, x, y, 0, dy)) {
                // 向上滑动
            } else if (dy > 0 && isOpen) {
                //菜单已展开, 且向下滑动了, 拦截
            } else {
                drag = false;
            }
            return drag;
        }

        @Override
        int xyToDistance(int dx, int dy) {
            return -dy;
        }

        @Override
        protected Boolean shouldOpenOnRelease(int xvel, int yvel) {
            if (yvel < -MinVelSettle) {
                return true;
            } else if (yvel > MinVelSettle) {
                return false;
            }
            return null;
        }

        @Override
        int drawerMarginEdge() {
            return ((LayoutParams)drawerView.getLayoutParams()).bottomMargin;
        }

        @Override
        void offsetDrawerDistance(View view, int distance) {
            view.offsetTopAndBottom(-distance);
        }

        @Override
        public void layoutDrawerView() {
            final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
            final int width = drawerView.getMeasuredWidth();
            final int height = drawerView.getMeasuredHeight();
            int top = drawerLayout.getHeight() - lp.bottomMargin - (isDrawerOpen() || !drawerShouldScroll() ? height : 0);
            int left = lp.leftMargin;
            drawerView.layout(left, top, left + width, top + height);
        }

        @Override
        public void layoutBodyView() {
            final LayoutParams lp = (LayoutParams) bodyView.getLayoutParams();
            final int width = bodyView.getMeasuredWidth();
            final int height = bodyView.getMeasuredHeight();
            int left = lp.leftMargin;
            int top = lp.topMargin;
            if (isDrawerOpen() && bodyShouldScroll()) {
                top -= drawerSize();
            }
            bodyView.layout(left, top, left + width, top + height);
        }

        @Override
        public void getBodyRect(RectF rect) {
            rect.set(0, 0, drawerLayout.getWidth(), drawerView.getTop());
        }
    }


    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        } else if (lp instanceof MarginLayoutParams) {
            return new LayoutParams((MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends MarginLayoutParams {
        public static final int ApplyWindowInsets_None = 0;
        public static final int ApplyWindowInsets_Margin = 1;
        public static final int ApplyWindowInsets_Padding = 2;

        float pullToOpenPosition = 0.5f;//下拉到高度的0.5倍的时候打开
        float pullToClosePosition = 0.5f;//上拉到高度的0.5倍的时候关闭
        int applyWindowInsets = ApplyWindowInsets_None;

        static final int CloseType_Normal = 0;//正常模式
        static final int CloseType_Allways = 1;//总是关闭
        int closeType = CloseType_Normal;

        static final int Mode_Cover = 0;
        static final int Mode_Concat = 1;
        int mode = Mode_Concat;

        boolean isOpen = false;
        int gravity = Gravity.NO_GRAVITY;
        float sizeWeight = 0;

        static final int[] LAYOUT_ATTRS = new int[]{
                android.R.attr.layout_gravity,
        };

        public LayoutParams(@NonNull Context c, @Nullable AttributeSet attrs) {
            super(c, attrs);
            {
                final TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
                this.gravity = a.getInt(0, Gravity.NO_GRAVITY);
                a.recycle();
            }
            {

                final TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.DrawerLayout_LayoutParams);
                this.closeType = a.getInt(R.styleable.DrawerLayout_LayoutParams_layout_dlt_closeType, closeType);
                this.pullToClosePosition = a.getFloat(R.styleable.DrawerLayout_LayoutParams_layout_dlt_pullToClosePosition, pullToClosePosition);
                this.pullToOpenPosition = a.getFloat(R.styleable.DrawerLayout_LayoutParams_layout_dlt_pullToOpenPosition, pullToOpenPosition);
                this.applyWindowInsets = a.getInt(R.styleable.DrawerLayout_LayoutParams_layout_dlt_applyWindowInsets, applyWindowInsets);
                this.sizeWeight = a.getFloat(R.styleable.DrawerLayout_LayoutParams_layout_dlt_sizeWeight, sizeWeight);
                this.mode = a.getInt(R.styleable.DrawerLayout_LayoutParams_layout_dlt_mode, mode);
                a.recycle();
            }
        }

        public LayoutParams(int width, int height, int gravity) {
            this(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(@NonNull LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
            this.applyWindowInsets = source.applyWindowInsets;
            this.sizeWeight = source.sizeWeight;
            this.mode = source.mode;
            this.pullToClosePosition = source.pullToClosePosition;
            this.pullToOpenPosition = source.pullToOpenPosition;
            this.isOpen = source.isOpen;
            this.closeType = source.closeType;
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(@NonNull ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(@NonNull MarginLayoutParams source) {
            super(source);
        }
    }

    private static class DrawerDeledges {
        DrawerDeledge[] all = {new DrawerDeledgeLeft(), new DrawerDeledgeTop(), new DrawerDeledgeRight(), new DrawerDeledgeBottom()};
        DrawerDeledge left(){
            return all[0];
        }
        DrawerDeledge top(){
            return all[1];
        }
        DrawerDeledge right(){
            return all[2];
        }
        DrawerDeledge bottom(){
            return all[3];
        }

        void init(DrawerLayout drawerLayout, ViewScrollHelper scrollHelper) {
            for (DrawerDeledge d : all) {
                d.drawerLayout = drawerLayout;
                d.scrollHelper = scrollHelper;
            }
        }

        DrawerDeledge ofGravity(int gravity) {
            for (DrawerDeledge d : all) {
                if (d.gravity == gravity) {
                    return d;
                }
            }
            return null;
        }
        DrawerDeledge ofOpened() {
            for (DrawerDeledge d : all) {
                if (d.isDrawerOpen()) {
                    return d;
                }
            }
            return null;
        }
        DrawerDeledge ofView(View v) {
            for (DrawerDeledge d : all) {
                if (d.drawerView == v) {
                    return d;
                }
            }
            return null;
        }

    }

}
