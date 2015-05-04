package com.simas.vc.editor.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.MediaController;

/**
 * Created by Simas Abramovas on 2015 Apr 30.
 */

// ToDo When controls shown back button doesn't work == focus is stolen from activity's window
// ToDo Create custom Controls
	// Work with a VideoView (play/pause button is updated, SeekBar is moved, etc.)
	// Center the play/pause button
	// Don't create a new window, so focus is not stolen
		// Because of the new window, WindowLeak exceptions are thrown
			// http://stackoverflow.com/questions/21678444/android-mediaplayer-with-mediacontroller-logcat-error-activity-has-leaked-wind
	// Prevent a bug on Galaxy S2: when controls visible, portrait and scroll down => shadow follows

/**
 * Need to create a custom MediaController, because by default it's drawn on top of everything,
 * including the drawer...
 */
@SuppressLint("ViewConstructor")
final class Controls extends MediaController {

	private final String TAG = getClass().getName();
	private static final int SHOW_TIMEOUT = 3000; // msecs
	private OnVisibilityChangedListener mVisibilityListener;
	private final Handler mHandler;

	public Controls(Context context) {
		super(context);
		mHandler = new Handler(context.getMainLooper());

		// Remove from current parent
		ViewGroup vg = (ViewGroup) getParent();
		if (vg != null) {
			vg.removeView(this);
		}
	}

	/**
	 * Override showing the controls, by simply showing the {@code FrameLayout}. Will be
	 * automatically hidden when {@code timeout} expires.
	 * @param timeout    delay in milliseconds before the controls disappear
	 */
	@Override
	public void show(int timeout) {
		super.show(timeout);
		if (timeout == 0) timeout = SHOW_TIMEOUT;
		// Remove existing messages and callbacks
		mHandler.removeCallbacksAndMessages(null);

		// Show layout
		setVisibility(VISIBLE);

		// Queue hiding
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				hide();
			}
		}, timeout);

		// Call the listeners (if any)
		if (mVisibilityListener != null) {
			mVisibilityListener.onVisibilityChanged(true);
		}
	}

	/**
	 * Override showing the controls, by simply showing the {@code FrameLayout}. Will be
	 * automatically hidden when {@code SHOW_TIMEOUT} timeout expires.
	 */
	@Override
	public void show() {
		super.show();
		// Call the listeners (if any)
		if (mVisibilityListener != null) {
			mVisibilityListener.onVisibilityChanged(true);
		}
	}

	/**
	 * Override hiding the controls by hiding the {@code FrameLayout}
	 */
	@Override
	public void hide() {
		super.hide();
		// Remove existing messages and callbacks
		mHandler.removeCallbacksAndMessages(null);

		// Hide layout
		setVisibility(INVISIBLE);

		// Call the listeners (if any)
		if (mVisibilityListener != null) {
			mVisibilityListener.onVisibilityChanged(false);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return super.onTouchEvent(event);
	}

	/**
	 * Important to call the {@code View}'s method not the {@code MediaController}'s
	 * @return  true if the controls are currently shown, false otherwise.
	 */
	@Override
	public boolean isShowing() {
		return isShown();
	}

	public void setOnVisibilityChangedListener(OnVisibilityChangedListener listener) {
		mVisibilityListener = listener;
	}

	public void removeOnVisibilityChangedListener() {
		mVisibilityListener = null;
	}

	public interface OnVisibilityChangedListener {
		void onVisibilityChanged(boolean shown);
	}

}