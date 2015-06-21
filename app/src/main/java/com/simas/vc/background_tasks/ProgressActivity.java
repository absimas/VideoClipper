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
package com.simas.vc.background_tasks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.helpers.Utils;
import java.io.File;

/**
 * Displays the progress inside of an {@code Activity} that is customized to look like a dialog.
 * Note that broadcasts aren't sent when the screen is locked so the activity fields aren't updated.
 */
public class ProgressActivity extends AppCompatActivity {

	// Intent action and arguments
	private static final String KEY_PREVIOUS_INTENT = "previous_intent";
	private static final String KEY_PREVIOUS_FILE = "previous_file";
	private static final String KEY_PREVIOUS_TOTAL_DURATION = "previous_total_duration";
	public static final String ACTION_DIALOG_UPDATE = "dialog_update_action";
	public static final String ARG_TYPE = "type";
	public static final String ARG_CONTENT = "content";
	public static final String ARG_OUTPUT_FILE = "output_file";
	public static final String ARG_TOTAL_DURATION = "output_duration";
	public static final String ARG_CUR_DURATION = "current_duration";
	// Progress file keys
	private static final String FPS = "fps=";
	private static final String FRAME = "frame=";
	private static final String OUT_TIME = "out_time=";
	private static final String TOTAL_SIZE = "total_size=";

	private static File sProgressingFile;
	private final String TAG = getClass().getName();

	public enum Type {
		ERROR, PROGRESS, FINISHED
	}

	private TextView mMain, mInfo, mOutputName, mCurrentSize, mTotalDuration, mCurrentDuration,
			mCurrentFrame, mFPS;
	private View mOpenButton, mTableView;
	private Type mType;
	private File mOutputFile;
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		private final String TAG = getClass().getName();

		@Override
		public void onReceive(Context context, Intent intent) {
			// If currently showing a finished dialog, then ignore the updates. A new dialog in
			// such a case can only be called via startActivity when onCreate is called
			if (mType == Type.FINISHED) return;
			setIntent(intent);
			onNewIntent(getIntent());
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.progress_dialog);

		mMain = (TextView) findViewById(R.id.main);
		mInfo = (TextView) findViewById(R.id.info);
		mOutputName = (TextView) findViewById(R.id.output_name_value);
		mCurrentSize = (TextView) findViewById(R.id.cur_size_value);
		mTotalDuration = (TextView) findViewById(R.id.total_duration_value);
		mCurrentDuration = (TextView) findViewById(R.id.current_duration_value);
		mCurrentFrame = (TextView) findViewById(R.id.current_frame_value);
		mFPS = (TextView) findViewById(R.id.fps_value);

		mOpenButton = findViewById(R.id.open);
		mTableView = findViewById(R.id.table);

		// Restore state if set
		if (savedInstanceState != null) {
			// Fetch previous intent
			Intent intent = savedInstanceState.getParcelable(KEY_PREVIOUS_INTENT);
			setIntent(intent);
			mOutputFile = (File) savedInstanceState.getSerializable(KEY_PREVIOUS_FILE);
			mTotalDuration.setText(savedInstanceState.getString(KEY_PREVIOUS_TOTAL_DURATION));
		}

		onNewIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (intent == null || intent.getSerializableExtra(ARG_TYPE) == null) {
			return;
		}

		// Update layout if it type has changed
		Type newType = (Type) intent.getSerializableExtra(ARG_TYPE);
		if (newType != null && mType != newType) {
			updateLayout(newType);
		}
		// Update fields
		updateFields(intent);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(KEY_PREVIOUS_INTENT, getIntent());
		outState.putSerializable(KEY_PREVIOUS_FILE, mOutputFile);
		outState.putString(KEY_PREVIOUS_TOTAL_DURATION, mTotalDuration.getText().toString());
	}

	/**
	 * Update layout to the {@code newType}.
	 */
	private void updateLayout(Type newType) {
		// Re-inflate for the specific type and find the views
		switch (newType) {
			case ERROR: case FINISHED:
				// Finished type has an open button while others do not
				if (newType == Type.FINISHED) {
					setTitle(R.string.vc_finished);
					mOpenButton.setVisibility(View.VISIBLE);
				} else {
					setTitle(R.string.vc_failed);
					mOpenButton.setVisibility(View.INVISIBLE);
				}
				// Main and info TVs visible
				mMain.setVisibility(View.VISIBLE);
				mInfo.setVisibility(View.VISIBLE);

				// Table view only on progress type
				mTableView.setVisibility(View.GONE);
				break;
			case PROGRESS:
				setTitle(R.string.vc_working);
				mTableView.setVisibility(View.VISIBLE);

				// Main, info TVs and open button aren't visible on progress type
				mMain.setVisibility(View.GONE);
				mInfo.setVisibility(View.GONE);
				mOpenButton.setVisibility(View.INVISIBLE);
				break;
		}

		mType = newType;
	}

	private void updateFields(@NonNull Intent intent) {
		// Common fields
		// Output file name (update if changed)
		File outputFile = (File) intent.getSerializableExtra(ARG_OUTPUT_FILE);
		if (outputFile != null && !outputFile.equals(mOutputFile)) {
			mOutputFile = outputFile;
		}

		// Total duration (update if changed)
		String outputDuration = intent.getStringExtra(ARG_TOTAL_DURATION);
		if (outputDuration != null && !outputDuration.equals(mTotalDuration.getText())) {
			mTotalDuration.setText(outputDuration);
		}
		// Current duration (always update)
		mCurrentDuration.setText(intent.getStringExtra(ARG_CUR_DURATION));

		switch (mType) {
			case PROGRESS:
				String content = intent.getStringExtra(ARG_CONTENT);
				// Content mustn't be null for progress typed content
				if (content == null) return;

				// Output name
				if (mOutputFile != null) {
					mOutputName.setText(mOutputFile.getName());
				}

				// Current file size
				String output, value;
				int startIndex = content.indexOf(TOTAL_SIZE);
				int endIndex = content.indexOf('\n', startIndex);
				if (startIndex != -1 && endIndex != -1) {
					output = content.substring(startIndex, endIndex);
					value = output.replaceAll(TOTAL_SIZE, "");
					try {
						long bytes = Long.parseLong(value);
						mCurrentSize.setText(Utils.bytesToMb(bytes));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}
				}

				// Current frame
				startIndex = content.indexOf(FRAME);
				endIndex = content.indexOf('\n', startIndex);
				if (startIndex != -1 && endIndex != -1) {
					output = content.substring(startIndex, endIndex);
					value = output.replaceAll(FRAME, "");
					mCurrentFrame.setText(value);
				}

				// FPS
				startIndex = content.indexOf(FPS);
				endIndex = content.indexOf('\n', startIndex);
				if (startIndex != -1 && endIndex != -1) {
					output = content.substring(startIndex, endIndex);
					value = output.replaceAll(FPS, "");
					mFPS.setText(value);
				}
				break;
			case FINISHED:
				if (mOutputFile != null) {
					mMain.setText(String.format(getString(R.string.format_clipping_succeeded),
							mOutputFile.getName()));
				}
				mInfo.setText(R.string.open_below);
				break;
			case ERROR:
				// Include file path in the error if it's available
				if (mOutputFile != null) {
					mMain.setText(String.format(getString(R.string.format_clipping_failed),
							mOutputFile.getName()));
				} else {
					mMain.setText(String.format(getString(R.string.format_clipping_failed),
							getString(R.string.the_file)));
				}
				mInfo.setText(getString(R.string.try_again));
				break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		sProgressingFile = mOutputFile;
		registerReceiver(mReceiver, new IntentFilter(ACTION_DIALOG_UPDATE));
	}

	@Override
	protected void onPause() {
		super.onPause();
		sProgressingFile = null;
		unregisterReceiver(mReceiver);
	}

	public static File getProgressingFile() {
		return sProgressingFile;
	}

	public void onClose(View view) {
		finish();
	}

	public void onOpen(View view) {
		if (mOutputFile != null) {
			Intent intent = new Intent();
			intent.setAction(android.content.Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(new File(mOutputFile.getPath())), Utils.VIDEO_MIME);
			startActivity(intent);
		}
		finish();
	}

}
