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
package com.simas.vc.nav_drawer;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

// ToDo custom xml settingsas nurodantis ar rodyt arrow prie checked itemo ar ne
// ToDo CAB open + check = change background, CAB closed + check = arrow

/**
 * Custom layout for NavItems.
 */
public class NavItemLayout extends LinearLayout implements Checkable {

	private boolean mChecked;
	private OnCheckedChangeListener mCheckedListener;

	public NavItemLayout(Context context) {
		super(context);
	}

	public NavItemLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public NavItemLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public NavItemLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	public void setChecked(boolean checked) {
		if (mCheckedListener != null) {
			mCheckedListener.onCheckedChanged(this, checked);
		}
		mChecked = checked;
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	@Override
	public void toggle() {
		setChecked(!isChecked());
	}

	public void setOnCheckedChangedListener(OnCheckedChangeListener listener) {
		mCheckedListener = listener;
	}

	public interface OnCheckedChangeListener {
		void onCheckedChanged(LinearLayout view, boolean checked);
	}

}
