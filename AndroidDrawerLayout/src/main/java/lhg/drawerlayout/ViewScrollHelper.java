package lhg.drawerlayout;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Company:
 * Project:
 * Author: liuhaoge
 * Date: 2021/2/5 9:04
 * Note:  辅助处理拦截事件, 简化scroll代码
 */

public class ViewScrollHelper {
    private static final String TAG = "ViewScrollHelper";
    public static final int INVALID_POINTER = -1;

    public static final int EDGE_LEFT = 1 << 0;
    public static final int EDGE_RIGHT = 1 << 1;
    public static final int EDGE_TOP = 1 << 2;
    public static final int EDGE_BOTTOM = 1 << 3;

    public static final int DIRECTION_HORIZONTAL = 1 << 0;
    public static final int DIRECTION_VERTICAL = 1 << 1;
    public static final int DIRECTION_ALL = DIRECTION_HORIZONTAL | DIRECTION_VERTICAL;
    public static final int DIRECTION_NONE = 0;

    private static final int BASE_SETTLE_DURATION = 256; // ms
    private static final int MAX_SETTLE_DURATION = 600; // ms
    private final int mEdgeSize;

    // Distance to travel before a drag may begin
    protected int mTouchSlop;

    // Last known position/pointer tracking
    private int mActivePointerId = INVALID_POINTER;
    private int mLastMotionX;
    private int mLastMotionY;
    private int mInitMotionX;
    private int mInitMotionY;

    private VelocityTracker mVelocityTracker;
    private float mMaxVelocity;
    private float mMinVelocity;

    private final Callback mCallback;
    private final ViewGroup mView;
    private boolean mIsDragging;
    private OverScroller mScroller;
    private boolean wrongDirectionScrollFirst = false;

    public void setIsDragging(boolean b) {
        mIsDragging = b;
    }

    public interface Callback {
        int getScrollDirection();
        //有些View在滚动的时候并没有调用requestDisallowInterceptTouchEvent，例如SwipeRefreshLayout, 所以这里做一下限定，错误方向首先滚动的话，则不能拦截
        default boolean shouldDragIfWrongDirectionScrollFirst() {
            return false;
        }
        //返回false不可拖动
        boolean onDragBegin(View child, int x, int y, int dx, int dy, int edge);
        boolean onScroll(int x, int y, int dx, int dy);
        void onDragEnd(int xvel, int yvel);
    }

    /**
     * Interpolator defining the animation curve for mScroller
     */
    private static final Interpolator sInterpolator = t -> {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
    };


    public static ViewScrollHelper create(@NonNull ViewGroup view, @NonNull Callback cb) {
        return new ViewScrollHelper(view.getContext(), view, cb);
    }


    public static ViewScrollHelper create(@NonNull ViewGroup view, float sensitivity, @NonNull Callback cb) {
        final ViewScrollHelper helper = create(view, cb);
        helper.mTouchSlop = (int) (helper.mTouchSlop * (1 / sensitivity));
        return helper;
    }

    public ViewScrollHelper(@NonNull Context context, @NonNull ViewGroup view, @NonNull Callback cb) {
        if (view == null) {
            throw new IllegalArgumentException("view may not be null");
        }
        if (cb == null) {
            throw new IllegalArgumentException("Callback may not be null");
        }

        mView = view;
        mCallback = cb;

        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMaxVelocity = vc.getScaledMaximumFlingVelocity();
        mMinVelocity = vc.getScaledMinimumFlingVelocity();
        mScroller = new OverScroller(mView.getContext(), sInterpolator);
        final float density = context.getResources().getDisplayMetrics().density;
        mEdgeSize = (int) (20 /*dp*/ * density + 0.5f);
    }

    public OverScroller getScroller() {
        return mScroller;
    }

    public void setMinVelocity(float minVel) {
        mMinVelocity = minVel;
    }

    public float getMinVelocity() {
        return mMinVelocity;
    }

    public boolean isDragging() {
        return mIsDragging;
    }

    public int getActivePointerId() {
        return mActivePointerId;
    }


    public int getTouchSlop() {
        return mTouchSlop;
    }


    public void cancel() {
        mIsDragging = false;
        mActivePointerId = INVALID_POINTER;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }


    public int computeSettleDuration(int hDragRange, int vDragRange, int dx, int dy, int xvel, int yvel) {
        xvel = clampMag(xvel, (int) mMinVelocity, (int) mMaxVelocity);
        yvel = clampMag(yvel, (int) mMinVelocity, (int) mMaxVelocity);
        final int absDx = Math.abs(dx);
        final int absDy = Math.abs(dy);
        final int absXVel = Math.abs(xvel);
        final int absYVel = Math.abs(yvel);
        final int addedVel = absXVel + absYVel;
        final int addedDistance = absDx + absDy;

        final float xweight = xvel != 0 ? (float) absXVel / addedVel :
                (float) absDx / addedDistance;
        final float yweight = yvel != 0 ? (float) absYVel / addedVel :
                (float) absDy / addedDistance;

        int xduration = computeAxisDuration(dx, xvel, hDragRange);
        int yduration = computeAxisDuration(dy, yvel, vDragRange);

        return (int) (xduration * xweight + yduration * yweight);
    }

    private int computeAxisDuration(int delta, int velocity, int motionRange) {
        if (delta == 0) {
            return 0;
        }

        final int width = mView.getWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, (float) Math.abs(delta) / width);
        final float distance = halfWidth + halfWidth
                * distanceInfluenceForSnapDuration(distanceRatio);

        int duration;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float range = (float) Math.abs(delta) / motionRange;
            duration = (int) ((range + 1) * BASE_SETTLE_DURATION);
        }
        return Math.min(duration, MAX_SETTLE_DURATION);
    }


    private int clampMag(int value, int absMin, int absMax) {
        final int absValue = Math.abs(value);
        if (absValue < absMin) return 0;
        if (absValue > absMax) return value > 0 ? absMax : -absMax;
        return value;
    }


    private float clampMag(float value, float absMin, float absMax) {
        final float absValue = Math.abs(value);
        if (absValue < absMin) return 0;
        if (absValue > absMax) return value > 0 ? absMax : -absMax;
        return value;
    }

    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * (float) Math.PI / 2.0f;
        return (float) Math.sin(f);
    }



    public static boolean canScroll(@NonNull View v, boolean checkV, int dx, int dy, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight()
                        && y + scrollY >= child.getTop() && y + scrollY < child.getBottom()
                        && canScroll(child, true, dx, dy, x + scrollX - child.getLeft(),
                        y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ((dx != 0 && v.canScrollHorizontally(-dx)) || (dy != 0 && v.canScrollVertically(-dy)));
    }


    public boolean interceptTouchEvent(@NonNull MotionEvent ev) {
        final int action = ev.getActionMasked();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsDragging)) {
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            // Reset things for a new event stream, just in case we didn't get
            // the whole previous stream.
            cancel();
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                wrongDirectionScrollFirst = false;
                mInitMotionX = mLastMotionX = (int) ev.getX();
                mInitMotionY = mLastMotionY = (int) ev.getY();
                Log.i("RTYJK", "mInitMotionY +" + mInitMotionY) ;
                mActivePointerId = ev.getPointerId(0);
                mScroller.computeScrollOffset();
                mIsDragging = !mScroller.isFinished();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId + " in onInterceptTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(pointerIndex);
                final int y = (int) ev.getY(pointerIndex);

                if (!mIsDragging) {
                    int dx = x - mLastMotionX;
                    int dy = y - mLastMotionY;
                    boolean slop = checkTouchSlop(dx, dy);
                    if (!slop) {
                        dx = x - mInitMotionX;
                        dy = y - mInitMotionY;
                        slop = checkTouchSlop(dx, dy);
                    }
                    if (slop && !wrongDirectionScrollFirst) {
                        View toCapture = findTopChildUnder(mView, x, y);
                        if (toCapture != null) {
                            int edge = getEdge(dx, dy);
                            mIsDragging = mCallback.onDragBegin(toCapture, x, y, dx, dy, edge);
                        }
                    }
                }
                mLastMotionX = x;
                mLastMotionY = y;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                cancel();
                break;
            }
        }

        return mIsDragging;
    }

    private int getEdge(int dx, int dy) {
        int edge = 0;
        if (mInitMotionX == mLastMotionX && mInitMotionY == mLastMotionY) {
            if (dy > 0 && mInitMotionY < mView.getTop() + mEdgeSize) {
                edge |= EDGE_TOP;
            }
            if (dy < 0 && mInitMotionY > mView.getBottom() - mEdgeSize) {
                edge |= EDGE_BOTTOM;
            }
            if (dx > 0 && mInitMotionX < mView.getLeft() + mEdgeSize) {
                edge |= EDGE_LEFT;
            }
            if (dx < 0 && mInitMotionX > mView.getRight() - mEdgeSize) {
                edge |= EDGE_RIGHT;
            }
        }
        if (edge != 0) {
            StringBuilder sb = new StringBuilder();
            if ((edge & EDGE_TOP) != 0) {
                sb.append("TOP ");
            }
            if ((edge & EDGE_BOTTOM) != 0) {
                sb.append("BOTTOM ");
            }
            if ((edge & EDGE_LEFT) != 0) {
                sb.append("LEFT ");
            }
            if ((edge & EDGE_RIGHT) != 0) {
                sb.append("RIGHT ");
            }
            Log.i(TAG, "drag edge " + sb);
        }
        return edge;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        final int actionMasked = ev.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            // Reset things for a new event stream, just in case we didn't get
            // the whole previous stream.
            cancel();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
		mVelocityTracker.addMovement(ev);
    
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                if ((mIsDragging = !mScroller.isFinished())) {
                    final ViewParent parent = mView.getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                // Remember where the motion event started
                mInitMotionX = mLastMotionX = (int) ev.getX();
                mInitMotionY = mLastMotionY = (int) ev.getY();
                Log.i("RTYJK", "mInitMotionY +" + mInitMotionY) ;
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(activePointerIndex);
                final int y = (int) ev.getY(activePointerIndex);
                int dx = x - mLastMotionX;
                int dy = y - mLastMotionY;
                if (!mIsDragging) {
                    boolean slop = checkTouchSlop(dx, dy);
                    if (!slop) {
                        dx = x - mInitMotionX;
                        dy = y - mInitMotionY;
                        Log.i("RTYJK", "dy = " + dy);
                        slop = checkTouchSlop(dx, dy);
                    }
                    if (slop) {
                        View toCapture = findTopChildUnder(mView, x, y);
                        if (toCapture != null) {
                            int edge = getEdge(dx, dy);
                            mIsDragging = mCallback.onDragBegin(toCapture, x, y, dx, dy, edge);
                        }
                    }
                    if (mIsDragging) {
                        final ViewParent parent = mView.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                mLastMotionX = x;
                mLastMotionY = y;
                if (mIsDragging) {
                    mCallback.onScroll(x, y, dx, dy);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsDragging) {
                    releaseViewForPointerUp();
                }
                cancel();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    protected boolean checkTouchSlop(float dx, float dy) {
        final int direction = mCallback.getScrollDirection();
        if (direction == DIRECTION_ALL) {
            return dx * dx + dy * dy > mTouchSlop * mTouchSlop;
        } else if (direction == DIRECTION_HORIZONTAL) {
            if (!mCallback.shouldDragIfWrongDirectionScrollFirst() && !wrongDirectionScrollFirst) {
                wrongDirectionScrollFirst = (Math.abs(dy) > Math.abs(dx)) && (Math.abs(dy)> mTouchSlop);
            }
            return Math.abs(dx) > mTouchSlop;
        } else if (direction == DIRECTION_VERTICAL) {
            if (!mCallback.shouldDragIfWrongDirectionScrollFirst() && !wrongDirectionScrollFirst) {
                wrongDirectionScrollFirst = (Math.abs(dx) > Math.abs(dy)) && (Math.abs(dx)> mTouchSlop);
            }
            return Math.abs(dy) > mTouchSlop;
        }
        return false;
    }


    private void releaseViewForPointerUp() {
        mIsDragging = false;
        mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
        final float xvel = clampMag(
                mVelocityTracker.getXVelocity(mActivePointerId),
                mMinVelocity, mMaxVelocity);
        final float yvel = clampMag(
                mVelocityTracker.getYVelocity(mActivePointerId),
                mMinVelocity, mMaxVelocity);
        mCallback.onDragEnd((int)xvel, (int)yvel);
    }

    public boolean isViewUnder(@Nullable View view, int x, int y) {
        if (view == null) {
            return false;
        }
        return x >= view.getLeft()
                && x < view.getRight()
                && y >= view.getTop()
                && y < view.getBottom();
    }


    @Nullable
    public static View findTopChildUnder(ViewGroup parent, int x, int y) {
        final int childCount = parent.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (x >= child.getLeft() && x < child.getRight()
                    && y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    public static View findTopChildUnder(ViewGroup parent,float x, float y) {
        return findTopChildUnder(parent, (int)x, (int)y);
    }

}

