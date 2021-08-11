package lhg.drawerlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;


/**
 * Company:
 * Project:
 * Author: liuhaoge
 * Date: 2021/1/1 10:21
 下拉露出背后或者顶部的的view, 最多只能添加两个view
 */
public class PullDownLayout extends FrameLayout {
    private static final String TAG = "PullDownLayout";
    public static final int CloseType_Normal = 0;//正常模式
    public static final int CloseType_Allways = 1;//总是关闭
    private int mCloseType = CloseType_Normal;
    private boolean mForbidOpen = false;//true 禁止下拉打开

    public static final int Mode_Under = 0;
    public static final int Mode_Top = 1;
    private static final int INVALID_POINTER = -1;
    private static int MinVelSettle = 1000;//松手不归位的最小速度

    private View mHeadView;
    private View mBodyView;
    private int mMode = Mode_Under;
    // 后面菜单的高度
    private int mHeadHeight;
    private boolean mHeadIsOpen;
    private float mPullToOpenPosition = 0.5f;//下拉到高度的0.5倍的时候打开
    private float mPullToClosePosition = 0.5f;//上拉到高度的0.5倍的时候关闭
    private PullDownListener mPullDownListener;

    OverScroller mScroller;
    ViewScrollHelper mScrollHelper;
    DispatchTouchEventHelper mDispatchTouchEventHelper;

    public PullDownLayout(Context context) {
        this(context, null);
    }

    public PullDownLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public PullDownLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PullDownLayout);
            this.mMode = a.getInt(R.styleable.PullDownLayout_pdl_mode, Mode_Under);
            this.mCloseType = a.getInt(R.styleable.PullDownLayout_pdl_closeType, CloseType_Normal);
            this.mPullToClosePosition = a.getFloat(R.styleable.PullDownLayout_pdl_pullToClosePosition, mPullToClosePosition);
            this.mPullToOpenPosition = a.getFloat(R.styleable.PullDownLayout_pdl_pullToOpenPosition, mPullToOpenPosition);
            a.recycle();
        }
        mScrollHelper = ViewScrollHelper.create(this, mDragHelperCallback);
        mScroller = mScrollHelper.getScroller();
        mDispatchTouchEventHelper = new DispatchTouchEventHelper();
    }

    public PullDownListener getPullDownListener() {
        return mPullDownListener;
    }

    public void setPullDownListener(PullDownListener pullDownListener) {
        this.mPullDownListener = pullDownListener;
    }

    public View getHeadView() {
        return mHeadView;
    }

    public View getBodyView() {
        return mBodyView;
    }

    public void setMode(int menuMode) {
        mMode = menuMode;
        requestLayout();
    }

    public int getMode() {
        return mMode;
    }

    public void setCloseType(int closeType) {
        this.mCloseType = closeType;
    }

    public int getCloseType() {
        return mCloseType;
    }

    public void setForbidOpen(boolean forbidOpen) {
        this.mForbidOpen = forbidOpen;
    }

    public boolean isForbidOpen() {
        return mForbidOpen;
    }

    public void setPullToOpenPosition(float pullToOpenPosition) {
        this.mPullToOpenPosition = pullToOpenPosition;
    }

    public void setPullToClosePosition(float pullToClosePosition) {
        this.mPullToClosePosition = pullToClosePosition;
    }

    public boolean isOpen() {
        return mHeadIsOpen;
    }

    //禁止child设置true
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (mHeadIsOpen && mDispatchTouchEventHelper.isMovingUp()) {
            super.requestDisallowInterceptTouchEvent(false);
        } else {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mDispatchTouchEventHelper.dispatchTouchEvent(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mScrollHelper.interceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mScrollHelper.onTouchEvent(ev);
    }

    private void stopChildScroll(View root) {
        ViewCompat.stopNestedScroll(root, ViewCompat.TYPE_NON_TOUCH);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                stopChildScroll(group.getChildAt(i));
            }
        }
    }

    private final ViewScrollHelper.Callback mDragHelperCallback = new ViewScrollHelper.Callback() {

        @Override
        public boolean shouldDragIfWrongDirectionScrollFirst() {
            return true;
        }

        @Override
        public int getScrollDirection() {
            return ViewScrollHelper.DIRECTION_VERTICAL;
        }

        @Override
        public boolean onDragBegin(View child, int x, int y, int dx, int dy, int edge) {
            if (mForbidOpen) {
                return false;//禁止拖动
            }
            boolean drag = true;
            if ((edge & ViewScrollHelper.EDGE_TOP) == ViewScrollHelper.EDGE_TOP) {
            } else if (!mScroller.isFinished()) {
            } else if (y < mBodyView.getTop()) {
            } else if(dy > 0 && !mHeadIsOpen && !canViewScroll(child, x, y, 0, dy)){
                // 向下滑动
            } else if (dy < 0 && mHeadIsOpen) {
                //菜单已展开, 且向上滑动了, 拦截
            } else {
                drag = false;
            }
            if (drag) {
                stopChildScroll(mBodyView);
            }
            return drag;
        }

        @Override
        public boolean onScroll(int x, int y, int dx, int dy) {
            //手指从上往下dy>0
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            scrollBodyBy(dy);
            return true;
        }

        @Override
        public void onDragEnd(int xvel, int yvel) {
            //手指从上往下 yvel>0
            boolean menuIsOpen;
            int topOffset = getBodyOffsetTop();
            if (yvel > MinVelSettle) {
                menuIsOpen = true;
            } else if (yvel < -MinVelSettle) {
                menuIsOpen = false;
            } else if (mHeadIsOpen) {
                menuIsOpen = topOffset > mHeadHeight * mPullToClosePosition;
            } else {
                menuIsOpen = topOffset > mHeadHeight * mPullToOpenPosition;
            }

            if (mCloseType == CloseType_Allways) {
                menuIsOpen = false;
            }
            settleLayout(menuIsOpen);
        }
    };

    public void close() {
        if (mHeadIsOpen) {
            settleLayout(false);
        }
    }

    public void open() {
        if (!mHeadIsOpen) {
            settleLayout(true);
        }
    }

    //释放layout归位
    private void settleLayout(boolean open) {
        boolean stateChanged = (open != mHeadIsOpen);
        mHeadIsOpen = open;
        int topOffset = getBodyOffsetTop();
        int dy = mHeadIsOpen ? mHeadHeight - topOffset : -topOffset;
        int duration = mScrollHelper.computeSettleDuration(0, mHeadHeight, 0, dy, 0, 0);
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        mScroller.startScroll(0, topOffset, 0, dy, duration);
        invalidate();
        if (stateChanged) {
            post(stateChangedRunnable);
        }
    }

    private final Runnable stateChangedRunnable = new Runnable() {
        @Override
        public void run() {
            postStateChanged(mHeadIsOpen);
        }
    };

    private void postStateChanged(boolean open) {
        if (mPullDownListener != null) {
            if (open) {
                mPullDownListener.onHeadOpened(this);
            } else {
                mPullDownListener.onHeadClosed(this);
            }
        }
    }

    @Override
    public void onViewAdded(View child) {
        if (getChildCount() > 2) {
            throw new IllegalStateException("只能包含两个child view");
        }
        if (getChildCount() == 1) {
            mHeadView = child;
        }
        if (getChildCount() == 2) {
            mBodyView = child;
        }
        super.onViewAdded(child);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (mBodyView == child) {
            mBodyView = null;
        }
        if (mHeadView == child) {
            mHeadView = null;
        }
    }


    public boolean canViewScroll(View view, int x, int y, int dx, int dy) {
        return mScrollHelper.canScroll(view, true, dx, dy,x - view.getLeft() + view.getScrollX(), y - view.getTop() + view.getScrollY());
    }


    //永远>=0
    public int getBodyOffsetTop() {
        LayoutParams lpbody = (LayoutParams) mBodyView.getLayoutParams();
        return mBodyView.getTop() - lpbody.topMargin;
    }

    public void scrollBodyBy(int dy) {
        if (mBodyView == null) {
            return;
        }
        int topOffset = -getBodyOffsetTop();
        int oldTop = -topOffset;
        int minTop = getPaddingTop();
        int maxTop = mHeadHeight + getPaddingTop();
        if (dy + oldTop < minTop) {
            dy = minTop - oldTop;
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
        } else if (dy + oldTop > maxTop) {
            dy = maxTop - oldTop;
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
        }

        if (dy != 0) {
            mBodyView.offsetTopAndBottom(dy);
        }
        if (mMode == Mode_Top) {
            LayoutParams lpbody = (LayoutParams) mBodyView.getLayoutParams();
            oldTop = mHeadView.getTop();
            int newTop = mBodyView.getTop() - lpbody.topMargin - mHeadHeight;
            mHeadView.offsetTopAndBottom(newTop - oldTop);
        } else if (mMode == Mode_Under) {
            LayoutParams lp = (LayoutParams) mHeadView.getLayoutParams();
            int top = getPaddingTop() + lp.topMargin;
            if (mHeadView.getTop() != top) {
                mHeadView.offsetTopAndBottom(top - mHeadView.getTop());
            }
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollBodyBy(mScroller.getCurrY() - getBodyOffsetTop());
            invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();
        final int parentLeft = getPaddingLeft();
        final int parentTop = getPaddingTop();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();
            int childLeft = parentLeft + lp.leftMargin;
            int childTop = parentTop + lp.topMargin;
            if (i == 0) {
                mHeadHeight = height + lp.topMargin + lp.bottomMargin;
            }
            if (i == 1 && mHeadIsOpen) {
                childTop += mHeadHeight;
            } else if (i == 0 && mMode == Mode_Top && !mHeadIsOpen) {
                childTop = parentTop - lp.bottomMargin - height;
            }
            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
    }



    public interface PullDownListener {
        void onHeadOpened(@NonNull PullDownLayout v);
        void onHeadClosed(@NonNull PullDownLayout v);
    }

    static class DispatchTouchEventHelper {

        private int mActivePointerId = INVALID_POINTER;
        static final int MoveDirection_None = 0;
        static final int MoveDirection_Other = 1;
        static final int MoveDirection_Up = 2;
        int moveDirection = MoveDirection_None;
        int lastMotionY = 0;
        int lastMotionX = 0;

        public void dispatchTouchEvent(PullDownLayout view, MotionEvent ev) {
            final int action = ev.getActionMasked();
            final int actionIndex = ev.getActionIndex();
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    moveDirection = MoveDirection_None;
                    final int index = ev.getActionIndex();
                    lastMotionX = (int)ev.getX(actionIndex);
                    lastMotionY = (int) ev.getY(actionIndex);
                    mActivePointerId = ev.getPointerId(index);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (moveDirection != MoveDirection_None) {
                        break;
                    }
                    final int activePointerId = mActivePointerId;
                    if (activePointerId == INVALID_POINTER) {
                        // If we don't have a valid id, the touch down wasn't on content.
                        break;
                    }

                    final int pointerIndex = ev.findPointerIndex(activePointerId);
                    if (pointerIndex == -1) {
                        break;
                    }
                    int x =  (int) ev.getX(pointerIndex);
                    int y =  (int) ev.getY(pointerIndex);
                    if (y - lastMotionY < 0) {
                        if (Math.abs(x - lastMotionX) > Math.abs(y - lastMotionY) ) {
                            moveDirection = MoveDirection_Other;
                        } else {
                            moveDirection = MoveDirection_Up;
                            if (view.mHeadIsOpen) {
                                //当前打开状态, 且向上滑动手指, 则强制打开onintercept函数
                                view.mScrollHelper.setIsDragging(true);
                                view.requestDisallowInterceptTouchEvent(false);
                            }
                        }
                    }
                    lastMotionX = x;
                    lastMotionY = y;
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    onSecondaryPointerUp(ev);
                    if (view.mHeadIsOpen && isMovingUp()) {
                        //当前打开状态, 且向上滑动手指, 则强制打开onintercept函数
                        view.mScrollHelper.setIsDragging(true);
                        view.requestDisallowInterceptTouchEvent(false);
                    }
                    break;
                }
            }
        }

        private boolean isMovingUp() {
            return moveDirection == MoveDirection_Up;
        }

        private void onSecondaryPointerUp(MotionEvent ev) {
            final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                    MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            final int pointerId = ev.getPointerId(pointerIndex);
            if (pointerId == mActivePointerId) {
                final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                lastMotionY = (int) ev.getY(newPointerIndex);
                mActivePointerId = ev.getPointerId(newPointerIndex);
            }
        }
    }

//    ///////////////////
//    private int computeSettleDuration(View child, int dy, int yvel) {
//        float ratio = 1f;
//        if (mMaxVelocity - mMinVelocity > 0) {
//            yvel = Math.max(Math.abs(yvel), mMinVelocity);
//            yvel = Math.min(yvel, mMaxVelocity);
//            ratio = (yvel - mMinVelocity)*1.f/(mMaxVelocity-mMinVelocity) + 1.f;
//        }
//        if (mHeadHeight == 0) {
//            return 100;
//        }
//        int time = (int) (Math.abs(dy)*1.f/mHeadHeight*BASE_SETTLE_DURATION/ratio);
//        if (time < 100) {
//            time = 100;
//        }
//        Log.i(TAG, "computeSettleDuration=" + time);
//        return time;
//    }
//    private static final int BASE_SETTLE_DURATION = 500; // ms
}
