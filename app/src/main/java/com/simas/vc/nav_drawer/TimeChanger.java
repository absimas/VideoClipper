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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;
import com.simas.vc.R;

// ToDo < API 11 do an EditText, code check and appropriate layouts
// ToDo uzklausa pasiketus lengthui: crop start or end. If first/last stream then no need to ask
// ToDo need hours/milliseconds picker?

public class TimeChanger {

	private static final String TAG = "TimeChangerDialog";
	private NumberPicker mMinPicker;
	private NumberPicker mSecPicker;
	private TextView mTimeContainer;

	private int mInitialMins, mInitialSecs;

	public TimeChanger(Context context, int titleResId, ViewGroup container) {
		View contentView = View.inflate(context, R.layout.time_dialog, null);

		mMinPicker = (NumberPicker) contentView.findViewById(R.id.min);
		mMinPicker.setMaxValue(60);
		mMinPicker.setMinValue(0);
		mSecPicker = (NumberPicker) contentView.findViewById(R.id.sec);
		mSecPicker.setMaxValue(60);
		mSecPicker.setMinValue(0);

		// Save the container
		mTimeContainer = (TextView) container.getChildAt(1);
		// Fetch the min, sec values
		String str = String.valueOf(mTimeContainer.getText());
		if (!TextUtils.isEmpty(str)) {
			String[] time = str.split(":");
			try {
				mInitialMins = Integer.parseInt(time[0]);
				mInitialMins = Integer.parseInt(time[1]);
			} catch (NumberFormatException e) {
				Log.e(TAG, "Failed to parse: '" + str + "'", e);
				mInitialMins = 0;
				mInitialSecs = 0;
			}
		}

		new AlertDialog.Builder(context)
				.setTitle(titleResId)
				.setView(contentView)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						updateTime();
					}
				})
				.show();
	}

	private void updateTime() {
		if (mMinPicker.getValue() != mInitialMins || mSecPicker.getValue() != mInitialSecs) {
			mTimeContainer.setText(String.format("%02d:%02d",
					mMinPicker.getValue(), mSecPicker.getValue()));
		}
	}

}
