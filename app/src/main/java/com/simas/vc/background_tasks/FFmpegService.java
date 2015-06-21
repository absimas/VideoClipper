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

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import com.simas.vc.helpers.Utils;
import com.simas.vc.VC;
import com.simas.vc.R;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Connects to FFmpeg library via JNI inside of a separate process.
 */
public class FFmpegService extends IntentService {

	private static final String TAG = "FFmpegService";
	private static final int INITIAL_ID = 100000;
	private static int sTaskCount = INITIAL_ID;
	private static int sIntentQueueSize;
	private static final NotificationManager NOTIFICATION_MANAGER = (NotificationManager)
			VC.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
	/**
	 * Used to keep the CPU working while the screen is locked.
	 */
	private static final String WAKE_LOCK_TAG = "FFmpeg";
	private final PowerManager.WakeLock mWakeLock =
			((PowerManager) VC.getAppContext().getSystemService(POWER_SERVICE))
					.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);


	// Argument keys
	public static final String ARG_EXEC_ARGS = "argc_n_argv";
	public static final String ARG_OUTPUT_FILE = "output_file";
	public static final String ARG_PROGRESS_FILE = "progress_file";
	public static final String ARG_OUTPUT_DURATION = "output_length";

	public FFmpegService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// Show notification
		String[] args = intent.getStringArrayExtra(ARG_EXEC_ARGS);
		File output = (File) intent.getSerializableExtra(ARG_OUTPUT_FILE);
		File progress = (File) intent.getSerializableExtra(ARG_PROGRESS_FILE);
		int duration = intent.getIntExtra(ARG_OUTPUT_DURATION, 0);

		// Launch progress notifier
		ProgressNotifier notifier = new ProgressNotifier(duration, output, progress);
		notifier.execute();
		// Launch the process itself
		int ffmpegResult = FFmpeg.cFFmpeg(args);
		notifier.ffmpegCancel(ffmpegResult);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Enable more releases than acquirements
		mWakeLock.setReferenceCounted(false);
		// Start the lock
		mWakeLock.acquire();

		// Increment queue size
		++sIntentQueueSize;
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Make sure the WakeLock is released
		mWakeLock.release();
	}

	private boolean isRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (getClass().getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private class ProgressNotifier extends AsyncTask<Void, Void, Boolean> {

		/**
		 * Amount of milliseconds to wait before re-checking the progress file
		 */
		private static final int PROGRESS_FILE_WAIT_DURATION = 1000;
		private static final String PROGRESS_KEY = "progress=";
		private static final String OUT_TIME_KEY = "out_time=";
		private static final String END_VALUE = "end";
		private static final String CONTINUE_VALUE = "continue";
		private static final int MAX_FRUITLESS_ITERATIONS = 5;

		private final int mDuration;
		private final String mDurationTime;
		private boolean mFfmpegSucceeded;
		private int mFfmpegReturnCode;
		private File mProgressLog;
		private File mOutput;
		private BufferedReader mReader;
		private NotificationCompat.Builder mBuilder;
		/**
		 * Intent used for broadcasting update messages. These are received by {@code
		 * ProgressActivity}
		 */
		private final Intent mUpdateIntent;
		/**
		 * Intent used to open up a {@code ProgressActivity}.
		 */
		private final Intent mDisplayIntent;
		private int mFruitlessIterations = 0;

		public ProgressNotifier(int outputDuration, File outputFile, File progressFile) {
			mDuration = outputDuration;
			mDurationTime = Utils.secsToTime(mDuration);
			mOutput = outputFile;
			mProgressLog = progressFile;
			++sTaskCount;
			mUpdateIntent = new Intent();
			mUpdateIntent.setAction(ProgressActivity.ACTION_DIALOG_UPDATE);
			mUpdateIntent.putExtra(ProgressActivity.ARG_TYPE, ProgressActivity.Type.PROGRESS);

			mDisplayIntent = new Intent(VC.getAppContext(), ProgressActivity.class);
			// Remove any existing progress activities, so onCreate is called instead of onNewIntent
			mDisplayIntent.setAction(ProgressActivity.ACTION_DIALOG_UPDATE);
			mDisplayIntent.putExtra(ProgressActivity.ARG_OUTPUT_FILE, outputFile);
			mDisplayIntent.putExtra(ProgressActivity.ARG_TOTAL_DURATION, mDurationTime);
			mDisplayIntent.putExtra(ProgressActivity.ARG_TYPE, ProgressActivity.Type.PROGRESS);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			// Open progress dialog on click
			PendingIntent pendingIntent = PendingIntent.getActivity(VC.getAppContext(), 0,
					mDisplayIntent, PendingIntent.FLAG_CANCEL_CURRENT);

			mBuilder = new NotificationCompat.Builder(getApplicationContext());
			mBuilder.setContentTitle(getString(R.string.vc_working))
					.setTicker(getString(R.string.initialising))
					.setContentText(getString(R.string.initialising))
					.setSmallIcon(R.drawable.ic_action_merge)
					.setContentIntent(pendingIntent)
					.setPriority(NotificationCompat.PRIORITY_MAX);

			// If length is not set, show an indeterminate progress notification
			if (mDuration < 1) {
				mBuilder.setProgress(mDuration, 0, true);
			}

			// Create an un-removable notification to display progress
			startForeground(INITIAL_ID, mBuilder.build());
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			for (;;) {
				try {
					if (isCancelled()) {
						// On older APIs the return value, when cancelled, is NOT ignored
						return mFfmpegSucceeded;
					}
					mReader = new BufferedReader(new FileReader(mProgressLog));

					StringBuilder sb = new StringBuilder();
					String line, lastLine = null;
					while ((line = mReader.readLine()) != null) {
						sb.append(line);
						lastLine = line;
						sb.append("\n");
					}

					// Full content/progress file
					String pg = sb.toString();

					// Make sure last line starts with the preferred key
					if (lastLine != null && lastLine.startsWith(PROGRESS_KEY)) {
						// Fruitful iteration
						mFruitlessIterations = 0;

						// Remove the last line
						int progressStart = pg.lastIndexOf(PROGRESS_KEY);
						pg = pg.substring(0, progressStart);

						// Trim pg to contain the last block
						int penultimateProgressStart = pg.lastIndexOf(PROGRESS_KEY);
						if (penultimateProgressStart != -1) {
							int penultimateProgressEnd = pg.indexOf('\n', penultimateProgressStart);
							if (penultimateProgressEnd != -1) {
								pg = pg.substring(penultimateProgressEnd, pg.length());
							}
						}

						// Check if end was reached
						String progress = lastLine.replaceAll(PROGRESS_KEY, "");
						if (progress.equals(END_VALUE)) {
							return true;
						} else if (mDuration > 0) { // If notification progress is not indeterminate
							// Calculate outTime start and end indexes
							int startIndex = pg.lastIndexOf(OUT_TIME_KEY);
							int endIndex = pg.indexOf("\n", startIndex);
							int secs = -1;
							if (startIndex != -1 && endIndex != -1) {
								// Parse outTime
								String outTime = pg.substring(startIndex, endIndex);
								outTime = outTime.replaceAll(OUT_TIME_KEY, "");
								secs = timeToSecs(outTime);
							}

							String curDur = null;
							if (secs == -1) {
								// Use an indeterminate progress instead
								mBuilder.setProgress(mDuration, 0, true);
								mBuilder.setContentText(getString(R.string.clipping));
							} else {
								mBuilder.setProgress(mDuration, secs, false);

								// max(currentDuration, totalDuration)
								if (secs >= mDuration) {
									curDur = mDurationTime;
								} else {
									curDur = Utils.secsToTime(secs);
								}

								mBuilder.setContentText(String.format("%s %s %s",
										curDur, getString(R.string.out_of), mDurationTime));
							}
							// Update display intent
							mDisplayIntent.putExtra(ProgressActivity.ARG_CUR_DURATION,curDur);
							mDisplayIntent.putExtra(ProgressActivity.ARG_CONTENT, pg);
							PendingIntent pIntent = PendingIntent.getActivity(VC.getAppContext(), 0,
									mDisplayIntent, PendingIntent.FLAG_CANCEL_CURRENT);
							mBuilder.setContentIntent(pIntent);

							// Update notification text (initially it's "initialising")
							mBuilder.setTicker(getString(R.string.clipping));

							// Update notification
							NOTIFICATION_MANAGER.notify(INITIAL_ID, mBuilder.build());

							// Send a broadcast message about the values update
							mUpdateIntent.putExtra(ProgressActivity.ARG_CONTENT, pg);
							mUpdateIntent.putExtra(ProgressActivity.ARG_CUR_DURATION,curDur);
							sendBroadcast(mUpdateIntent);
						}
					} else {
						if (++mFruitlessIterations >= MAX_FRUITLESS_ITERATIONS) {
							// Stop the service if max iterations reached without getting any data
							return false;
						}
					}
					Thread.sleep(PROGRESS_FILE_WAIT_DURATION);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException ignored) {
					Log.i(TAG, "Waiting has been interrupted... Probably by cancelled the task.");
				} finally {
					try {
						if (mReader != null) mReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		/**
		 * Cancel called after FFmpeg finishes.
		 */
		public void ffmpegCancel(int succeeded) {
			mFfmpegReturnCode = succeeded;
			mFfmpegSucceeded = (mFfmpegReturnCode == 0);
			if (cancel(true)) {
				Log.i(TAG, String.format("FFmpeg %s first, so it canceled the notifier.",
						(mFfmpegSucceeded) ? "succeeded" : "failed"));
			}
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			Log.i(TAG, String.format("Service cancelled. FFmpeg has %s",
					(mFfmpegSucceeded) ? "succeeded" : "failed"));
			finish();
		}

		@Override
		protected void onPostExecute(Boolean didFinish) {
			super.onPostExecute(didFinish);
			Log.i(TAG, String.format("Service finished because it %s.",
					(didFinish == null || !didFinish) ? "failed" : "succeeded"));
			mFfmpegSucceeded = !(didFinish == null || !didFinish);
			finish();
		}

		private void finish() {
			if (mFfmpegSucceeded) {
				showSuccessNotification();
			} else {
				showFailureNotification();
			}

			// Delete progress file
			mProgressLog.delete();

			// Cancel progress notification if the queue is empty
			if (--sIntentQueueSize <= 0) {
				sIntentQueueSize = 0;

				// Queue's done, release the WakeLock
				mWakeLock.release();

				// stopForeground will remove the notification with INITIAL_ID
				stopForeground(true);
			}
		}

		private void showFailureNotification() {
			// Open error dialog on click
			mDisplayIntent.putExtra(ProgressActivity.ARG_CONTENT, String.valueOf(mFfmpegReturnCode));
			mDisplayIntent.putExtra(ProgressActivity.ARG_TYPE, ProgressActivity.Type.ERROR);
			PendingIntent pendingIntent = PendingIntent.getActivity(VC.getAppContext(), 0,
					mDisplayIntent, PendingIntent.FLAG_CANCEL_CURRENT);

			// Show notification and a toast only if the progress for the current file isn't shown
			File progressingFile = ProgressActivity.getProgressingFile();
			if (progressingFile == null || progressingFile.compareTo(mOutput) != 0) {
				String str = String.format("android.resource://%s/%s", getPackageName(),R.raw.fail);
				mBuilder = new NotificationCompat.Builder(getApplicationContext());
				mBuilder.setContentTitle(getString(R.string.vc_failed))
						.setTicker(getString(R.string.vc_failed))
						.setContentText(String.format(
								getString(R.string.format_clipping_failed), mOutput.getName()))
						.setSmallIcon(R.drawable.ic_action_error)
						.setContentIntent(pendingIntent)
						.setAutoCancel(true)
						.setSound(Uri.parse(str));

				// Show the final notification
				NOTIFICATION_MANAGER.notify(sTaskCount, mBuilder.build());

				// Show a toast
				Toast.makeText(getApplicationContext(), R.string.clipping_failed_see_notification,
						Toast.LENGTH_LONG).show();
			}

			// Send a broadcast message about the occured error
			mUpdateIntent.putExtra(ProgressActivity.ARG_TYPE, ProgressActivity.Type.ERROR);
			sendBroadcast(mUpdateIntent);
		}

		private void showSuccessNotification() {
			// Open file on click
			Intent intent = new Intent();
			intent.setAction(android.content.Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(mOutput), Utils.VIDEO_MIME);
			PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
					intent, PendingIntent.FLAG_UPDATE_CURRENT);

			// Show notification and a toast only if the progress for the current file isn't shown
			File progressingFile = ProgressActivity.getProgressingFile();
			if (progressingFile == null || progressingFile.compareTo(mOutput) != 0) {
				String str = String.format("android.resource://%s/%s", getPackageName(), R.raw.ok);
				mBuilder = new NotificationCompat.Builder(getApplicationContext());
				mBuilder.setContentTitle(getString(R.string.vc_finished))
						.setTicker(getString(R.string.vc_finished))
						.setContentText(String.format(
								getString(R.string.format_click_to_open_video), mOutput.getName()))
						.setSmallIcon(R.drawable.ic_action_merge)
						.setContentIntent(pendingIntent)
						.setAutoCancel(true)
						.setSound(Uri.parse(str));

				// Show the final notification
				NOTIFICATION_MANAGER.notify(sTaskCount, mBuilder.build());

				// Show a toast
				Toast.makeText(getApplicationContext(), R.string.clipped_see_notification,
						Toast.LENGTH_LONG).show();
			}

			// Send a broadcast message about the values update
			mUpdateIntent.putExtra(ProgressActivity.ARG_TYPE, ProgressActivity.Type.FINISHED);
			sendBroadcast(mUpdateIntent);
		}

		/**
		 * Converts a time string to seconds.
		 * @param outTime    string in format of hh:mm:ss:millis
		 * @return -1 on error
		 */
		private int timeToSecs(final String outTime) {
			// Remove microseconds
			int dotIndex = outTime.indexOf(".");
			if (dotIndex == -1) return -1;

			String[] time = outTime.substring(0, dotIndex).split(":");
			if (time.length != 3) return -1;

			try {
				int secs = 0;
				secs += Integer.parseInt(time[0]) * 60 * 60;
				secs += Integer.parseInt(time[1]) * 60;
				secs += Integer.parseInt(time[2]);
				return secs;
			} catch (NumberFormatException e) {
				return -1;
			}
		}

	}

}
