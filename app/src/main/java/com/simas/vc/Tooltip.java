/*
 * Copyright (c) 2015. Simas Abramovas
 *
 * This file is part of VideoClipper.
 *
 * VideoClipper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VideoClipper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VideoClipper. If not, see <http://www.gnu.org/licenses/>.
 */
package com.simas.vc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RelativeLayout;
import android.widget.TextView;

// ToDo removal
// ToDo timeout
// ToDo oval (def tooltip) bg

@SuppressLint("ViewConstructor")
public class Tooltip extends RelativeLayout {

	private final String TAG = getClass().getName();
	private View mAnchor;
	private Window mWindow;
	private TextView mTextView;
	private String mText;

	public Tooltip(@NonNull Activity activity, @NonNull View anchor, @NonNull String text) {
		super(activity);
		mAnchor = anchor;
		mWindow = activity.getWindow();
		mText = text;
		init();
	}

	private void init() {
		// Add TextView as a child
		mTextView = new TextView(getContext());
		mTextView.setText(getText());
		RelativeLayout.LayoutParams textViewParams = new RelativeLayout.LayoutParams(
				200, RelativeLayout.LayoutParams.WRAP_CONTENT);
		mTextView.setLayoutParams(textViewParams);
		addView(mTextView);

		updatePosition();

		// Add initially invisible Tooltip to DecorView
		setVisibility(INVISIBLE);
		ViewGroup decor = (ViewGroup) getWindow().getDecorView();
		decor.addView(this);

		// tooltip.width = anchor.width
		final View anchor = getAnchor();
		Log.e(TAG, "anchor width: " + anchor.getWidth());
		if (anchor.getWidth() > 0) {
			ViewGroup.LayoutParams params = getLayoutParams();
			params.width = anchor.getWidth() - anchor.getPaddingLeft() - anchor.getPaddingRight();
			params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			setVisibility(VISIBLE);
			requestLayout();
		} else {
			// If anchor width is 0, consider it not drawn yet, and try to redraw it
			anchor.addOnLayoutChangeListener(new OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					anchor.removeOnLayoutChangeListener(this);
					ViewGroup.LayoutParams params = getLayoutParams();
					// If anchor's width is still 0, just wrap the Tooltip's content
					if (anchor.getWidth() == 0) {
						params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
					} else {
						params.width = getAnchor().getWidth();
					}
					params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
					setVisibility(VISIBLE);
					requestLayout();
				}
			});
			// Force anchor re-draw (to invoke LayoutChangeListener)
			anchor.requestLayout();
		}
	}

	public void updatePosition() {
		// Get anchor position on screen
		int[] coordinates = new int[2];
		getAnchor().getLocationInWindow(coordinates);
		int extraLeft = getAnchor().getPaddingLeft();
//		if (getAnchor() instanceof TextView) {
//			TextView tv = (TextView) getAnchor();
//			extraLeft += tv.getCompoundPaddingLeft();
//		}
		final int x = coordinates[0] + extraLeft;
		final int y = coordinates[1] + getAnchor().getHeight() + getAnchor().getPaddingTop();

		// Move the Tooltip there
		setX(x);
		setY(y);
	}

	public  Window getWindow() {
		return mWindow;
	}

	public View getAnchor() {
		return mAnchor;
	}

	public void setWindow(Window window) {
		mWindow = window;
	}

	public void setAnchor(View anchor) {
		mAnchor = anchor;
	}

	public void setText(String text) {
		mText = text;
		getTextView().setText(mText);
	}

	public String getText() {
		return mText;
	}

	public TextView getTextView() {
		return mTextView;
	}

}
