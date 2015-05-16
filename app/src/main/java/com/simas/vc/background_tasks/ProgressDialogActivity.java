package com.simas.vc.background_tasks;

import android.app.NotificationManager;
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

// ToDo if notification is removed toast shouldn't be shown.
// ToDo when locked, processes stop? >.> or perhaps broadcasts are not sent..
// ToDo when switching layouts do a transition. Resize would be nice.
// ToDo rotate on finished screen, brings back progress screen. Probly savedInstanceState.

public class ProgressDialogActivity extends AppCompatActivity {

	// Intent action and arguments
	public static final String ACTION_DIALOG_UPDATE = "dialog_update_action";
	public static final String ARG_TYPE = "type";
	public static final String ARG_CONTENT = "content";
	public static final String ARG_OUTPUT_FILE = "output_file";
	public static final String ARG_TOTAL_DURATION = "output_duration";
	public static final String ARG_CUR_DURATION = "current_duration";
	public static final String ARG_NOTIFICATION_ID = "notification_id";
	// Progress file keys
	private static final String FPS = "fps=";
	private static final String FRAME = "frame=";
	private static final String OUT_TIME = "out_time=";
	private static final String TOTAL_SIZE = "total_size=";

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
			if (intent == null || intent.getSerializableExtra(ARG_TYPE) == null) {
				finish();
				return;
			}

			updateLayout((Type) intent.getSerializableExtra(ARG_TYPE));
			String content = intent.getStringExtra(ARG_CONTENT);
			if (content == null) {
				finish();
				return;
			}
			mCurrentDuration = intent.getStringExtra(ARG_CUR_DURATION);
			updateFields(content);

			// Remove the notification when an intent of failure or finish is received
			// Worth to note that the reception is done only when the dialog is shown
			if (mType == Type.FINISHED || mType == Type.ERROR) {
				int notificationId = intent.getIntExtra(ARG_NOTIFICATION_ID, -1);
				if (notificationId != -1) {
					NotificationManager notificationManager = (NotificationManager)
							getSystemService(Context.NOTIFICATION_SERVICE);
					notificationManager.cancel(notificationId);
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent() == null || getIntent().getSerializableExtra(ARG_TYPE) == null) {
			finish();
			return;
		}

		mOutputFile = (File) getIntent().getSerializableExtra(ARG_OUTPUT_FILE);
		mOutputDuration = getIntent().getStringExtra(ARG_TOTAL_DURATION);
		mCurrentDuration = getIntent().getStringExtra(ARG_CUR_DURATION);

		updateLayout((Type) getIntent().getSerializableExtra(ARG_TYPE));
		String content = getIntent().getStringExtra(ARG_CONTENT);
		if (content != null) updateFields(content);
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
				mTextViews[2].setText((mOutputDuration != null) ? mOutputDuration : "");

				// Current duration
				mTextViews[3].setText((mCurrentDuration != null) ? mCurrentDuration : "");

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
		registerReceiver(mReceiver, new IntentFilter(ACTION_DIALOG_UPDATE));
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mReceiver);
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
