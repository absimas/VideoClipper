package com.simas.vc.editor.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.MediaController;
import java.lang.reflect.Field;

/**
 * Created by Simas Abramovas on 2015 Apr 30.
 */

// ToDo W/MediaPlayer﹕ Attempt to seek to invalid position: -3197
// ToDo Create custom Controls
	// Work with a VideoView (play/pause button is updated, SeekBar is moved, etc.)
	// Center the play/pause button
	// Don't create a new window, so focus is not stolen
		// Because of the new window, WindowLeak exceptions are thrown
			// http://stackoverflow.com/questions/21678444/android-mediaplayer-with-mediacontroller-logcat-error-activity-has-leaked-wind
	// Prevent a bug on Galaxy S2: when controls visible, portrait and scroll down => shadow follows

/**
 * Custom {@code MediaController} that provides more functionality than its predecessor.
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
		final ViewGroup vg = (ViewGroup) getParent();
		if (vg != null) {
			vg.removeView(this);
		}

		// Set the MediaController Window's DecorView, to INVISIBLE
		try {
			Field field = MediaController.class.getDeclaredField("mWindow");
			field.setAccessible(true);
			Window window = (Window) field.get(this);
			Window activityWindow = ((Activity)context).getWindow();
			if (window == null) {
				Log.w(TAG, "Couldn't get MediaController window.");
			} else if (window == activityWindow) {
				Log.w(TAG, "MediaController uses activity's window.");
			} else {
				window.getDecorView().setVisibility(GONE);
			}
		} catch (NoSuchFieldException | ClassCastException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
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