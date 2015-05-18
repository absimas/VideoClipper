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
import com.simas.vc.Utils;
import java.io.File;

/**
 * Created by Simas Abramovas on 2015 May 16.
 */

// ToDo when locked, processes stop? >.> or perhaps broadcasts are not sent..
// ToDo when switching layouts do a transition. Resize would be nice.

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

	private static boolean sActivityShown;
	private final String TAG = getClass().getName();

	public enum Type {
		ERROR, PROGRESS, FINISHED
	}

	private Type mType;
	private File mOutputFile;
	private String mCurrentDuration, mOutputDuration;
	private TextView[] mTextViews;
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		private final String TAG = getClass().getName();

		@Override
		public void onReceive(Context context, Intent intent) {
			setIntent(intent);
			onNewIntent(getIntent());
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Restore state if set
		if (savedInstanceState != null) {
			// Fetch previous intent
			Intent intent = savedInstanceState.getParcelable(KEY_PREVIOUS_INTENT);
			setIntent(intent);
			mOutputFile = (File) savedInstanceState.getSerializable(KEY_PREVIOUS_FILE);
			mOutputDuration = savedInstanceState.getString(KEY_PREVIOUS_TOTAL_DURATION);
		}

		onNewIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent == null || intent.getSerializableExtra(ARG_TYPE) == null) {
			return;
		}

		// Set fields if empty
		if (mOutputFile == null) {
			mOutputFile = (File) getIntent().getSerializableExtra(ARG_OUTPUT_FILE);
		}
		if (mOutputDuration == null) {
			mOutputDuration = getIntent().getStringExtra(ARG_TOTAL_DURATION);
		}
		// Current duration should be re-set each time
		mCurrentDuration = getIntent().getStringExtra(ARG_CUR_DURATION);

		// Change layout if needed (method will decide)
		updateLayout((Type) getIntent().getSerializableExtra(ARG_TYPE));
		// Change fields if content is set
		String content = getIntent().getStringExtra(ARG_CONTENT);
		if (content != null) updateFields(content);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(KEY_PREVIOUS_INTENT, getIntent());
		outState.putSerializable(KEY_PREVIOUS_FILE, mOutputFile);
		outState.putString(KEY_PREVIOUS_TOTAL_DURATION, mOutputDuration);
	}

	/**
	 * Update layout to the {@code newType}. If it's the same as before, do nothing.
	 */
	private void updateLayout(Type newType) {
		if (mType == newType) return;

		// Re-inflate for the specific type and find the views
		switch (newType) {
			case ERROR: case FINISHED:
				if (mType != Type.ERROR && mType != Type.FINISHED) {
					mTextViews = new TextView[2];
					setContentView(R.layout.activity_progress_done_dialog);
					mTextViews[0] = (TextView) findViewById(R.id.main);
					mTextViews[1] = (TextView) findViewById(R.id.info);
				}
				// When switching from error to finished or vice-versa, no need to re-inflate,
				// just hide or show the open button
				if (newType == Type.FINISHED) {
					setTitle(R.string.vc_finished);
					findViewById(R.id.open).setVisibility(View.VISIBLE);
				} else {
					setTitle(R.string.vc_failed);
					findViewById(R.id.open).setVisibility(View.INVISIBLE);
				}
				break;
			case PROGRESS:
				setTitle(R.string.vc_working);
				setContentView(R.layout.activity_progress_dialog);
				mTextViews = new TextView[6];
				mTextViews[0] = (TextView) findViewById(R.id.output_name_value);
				mTextViews[1] = (TextView) findViewById(R.id.cur_size_value);
				mTextViews[2] = (TextView) findViewById(R.id.total_duration_value);
				mTextViews[3] = (TextView) findViewById(R.id.current_duration_value);
				mTextViews[4] = (TextView) findViewById(R.id.current_frame_value);
				mTextViews[5] = (TextView) findViewById(R.id.fps_value);
				break;
		}

		mType = newType;
	}

	private void updateFields(@NonNull String content) {
		switch (mType) {
			case PROGRESS:
				// Output name
				if (mOutputFile != null) {
					mTextViews[0].setText(mOutputFile.getName());
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
						double mb = bytes / 1024.0 / 1024.0;
						mTextViews[1].setText(String.format("%.2f MB", mb));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}
				}

				// Total duration
				mTextViews[2].setText(mOutputDuration);

				// Current duration
				mTextViews[3].setText(mCurrentDuration);

				// Current frame
				startIndex = content.indexOf(FRAME);
				endIndex = content.indexOf('\n', startIndex);
				if (startIndex != -1 && endIndex != -1) {
					output = content.substring(startIndex, endIndex);
					value = output.replaceAll(FRAME, "");
					mTextViews[4].setText(value);
				}

				// FPS
				startIndex = content.indexOf(FPS);
				endIndex = content.indexOf('\n', startIndex);
				if (startIndex != -1 && endIndex != -1) {
					output = content.substring(startIndex, endIndex);
					value = output.replaceAll(FPS, "");
					mTextViews[5].setText(value);
				}
				break;
			case FINISHED:
				if (mOutputFile != null) {
					mTextViews[0].setText(String.format(getString(R.string.format_clipping_succeeded),
							mOutputFile.getName()));
				}
				mTextViews[1].setText(R.string.open_below);
				break;
			case ERROR:
				if (mOutputFile != null) {
					mTextViews[0].setText(String.format(getString(R.string.format_clipping_failed),
							mOutputFile.getName()) + "\nError code returned was:");
				}
				mTextViews[1].setText(content);
				break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		sActivityShown = true;
		registerReceiver(mReceiver, new IntentFilter(ACTION_DIALOG_UPDATE));
	}

	@Override
	protected void onPause() {
		super.onPause();
		sActivityShown = false;
		unregisterReceiver(mReceiver);
	}

	public static boolean isShown() {
		return sActivityShown;
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
