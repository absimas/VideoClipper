package com.simas.vc.nav_drawer;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Selection;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

/**
 * Created by Simas Abramovas on 2015 Mar 08.
 */

// ToDo custom xml settingsas nurodantis ar rodyt arrow prie checked itemo ar ne
// ToDo CAB open + check = change background, CAB closed + check = arrow

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
