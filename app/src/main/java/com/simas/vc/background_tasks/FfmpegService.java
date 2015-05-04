package com.simas.vc.background_tasks;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.simas.vc.VC;
import com.simas.vc.R;
import com.simas.vc.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by Simas Abramovas on 2015 Mar 16.
 */

public class FfmpegService extends IntentService {

	// ToDo kill process if progress file is empty or malformed
	// ToDo checkout fancy calls:
//	ffmpeg -i concat:1\ 2.mp4\|2.mp4 3.mp4
	// concats to some different encoding, check out later

//	ffmpeg -i "/home/toto/.ekd_tmp/ekd_toto/video_extension_resol/file_001.mp4"
//			-i "/home/toto/.ekd_tmp/ekd_toto/video_extension_resol/file_002.mp4"
//			-filter_complex concat=n=2:v=1:a=1 -codec:v mjpeg -b:v 5000k -an
//	-threads 4 -y "/home/toto/.ekd_tmp/ekd_toto/video.avi"

	// Statics
	private static final String TAG = "FfmpegService";
	private static final int INITIAL_ID = 100000;
	private static int sTaskCount = INITIAL_ID;
	private static int sIntentQueueSize;
	private static final NotificationManager NOTIFICATION_MANAGER = (NotificationManager)
			VC.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);

	// Argument keys
	public static final String ARG_EXEC_ARGS = "argc_n_argv";
	public static final String ARG_INPUT_FILE = "input_file";
	public static final String ARG_OUTPUT_FILE = "output_file";
	public static final String ARG_PROGRESS_FILE = "progress_file";
	public static final String ARG_OUTPUT_LENGTH = "output_length";

	public FfmpegService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// Show notification
		String[] args = intent.getStringArrayExtra(ARG_EXEC_ARGS);
		File output = (File) intent.getSerializableExtra(ARG_OUTPUT_FILE);
		File progress = (File) intent.getSerializableExtra(ARG_PROGRESS_FILE);
		int length = intent.getIntExtra(ARG_OUTPUT_LENGTH, 0);

		// Launch progress notifier
		ProgressNotifier notifier = new ProgressNotifier(length, output, progress);
		notifier.execute();
		// Launch the process itself
		boolean ffmpegResult = Ffmpeg.cFfmpeg(args);
		notifier.cancelBecause(ffmpegResult);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		++sIntentQueueSize;
		return super.onStartCommand(intent, flags, startId);
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

		private static final String PROGRESS_KEY = "progress=";
		private static final String OUT_TIME_KEY = "out_time=";
		private static final String END_VALUE = "end";
		private static final String VIDEO_MIME = "video";
		private static final String CONTINUE_VALUE = "continue";

		private final int mTaskNum = ++sTaskCount;
		private final int mOutputLength;
		private final String mOutputLengthStr;
		private boolean mFfmpegSucceeded;
		private File mProgressLog;
		private File mOutput;
		private BufferedReader mReader;
		private NotificationCompat.Builder mBuilder;

		public ProgressNotifier(int outputLength, File outputFile, File progressFile) {
			mOutputLength = outputLength;
			mOutputLengthStr = secondsToTime(outputLength);
			mOutput = outputFile;
			mProgressLog = progressFile;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mBuilder = new NotificationCompat.Builder(VC.getAppContext());
			mBuilder.setContentTitle(Utils.getString(R.string.vc_working))
					.setContentText(Utils.getString(R.string.clipping))
					.setSmallIcon(R.drawable.ic_action_merge);

			// If length is not set, show an indeterminate progress notification
			if (mOutputLength < 1) {
				mBuilder.setProgress(mOutputLength, 0, true);
			}
			// Create an un-removable notification to display progress
			startForeground(INITIAL_ID, mBuilder.build());
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			int i = 0;
			for (;;) {
				try {
					if (isCancelled()) {
						// On older APIs the return value, when cancelled, is NOT ignored
						return mFfmpegSucceeded;
					}
					i++;
					mReader = new BufferedReader(new FileReader(mProgressLog));

					StringBuilder sb = new StringBuilder();
					String line, lastLine = null;
					while ((line = mReader.readLine()) != null) {
						sb.append(line);
						lastLine = line;
						sb.append("\n");
					}

					// Make sure last line starts with the preferred key
					if (lastLine != null && lastLine.startsWith(PROGRESS_KEY)) {
						// Check if progress ended
						if (lastLine.replaceAll(PROGRESS_KEY, "").equals(END_VALUE)) {
							return true;
						} else if (mOutputLength > 0) {
							int index = sb.lastIndexOf(OUT_TIME_KEY);
							if (index != -1) {
								int outTimeEndIndex = sb.indexOf("\n", index);
								if (outTimeEndIndex != -1) {
									// Parse out time
									String outTime = sb.substring(index, outTimeEndIndex);
									outTime = outTime.replaceAll(OUT_TIME_KEY, "");
									int secs = outTimeToSeconds(outTime);
									if (secs == -1) {
										Log.e(TAG, "Failed to parse progress time: " + outTime);
										// On fail use an indeterminate progress
										mBuilder.setProgress(mOutputLength, 0, true);
									} else {
										mBuilder.setProgress(mOutputLength, secs, false);
										mBuilder.setContentText(String.format("%s out of %s",
												secondsToTime(secs), mOutputLengthStr));
									}
									NOTIFICATION_MANAGER.notify(INITIAL_ID, mBuilder.build());
								}
							}
						}
					}
					Thread.sleep(1000);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException ignored) {
					Log.i(TAG, "Waiting has been interrupted... Probably by cancelling the task.");
				} finally {
					try {
						if (mReader != null) mReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		public void cancelBecause(boolean succeeded) {
			mFfmpegSucceeded = succeeded;
			if (cancel(true)) {
				Log.i(TAG, String.format("FFmpeg %s first, so it canceled the notifier.",
						(succeeded) ? "succeeded" : "failed"));
			}
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			Log.i(TAG, String.format("Notifier exits because it was cancelled. FFmpeg has %s",
					(mFfmpegSucceeded) ? "succeeded" : "failed"));
			if (mFfmpegSucceeded) {
				// FFmpeg succeeded and is killing progress task earlier, so just quietly
				// quit, by showing the end notification
				showFinalNotification();

			} else {
				// FFmpeg task failed, remove notification
				removeNotification();
			}
			mProgressLog.delete();
		}

		@Override
		protected void onPostExecute(Boolean didFinish) {
			super.onPostExecute(didFinish);
			Log.i(TAG, String.format("Notifier finishes because it %s.",
					(didFinish != null && didFinish) ? "succeeded" : "failed"));
			if (didFinish == null || !didFinish) {
				removeNotification();
			} else {
				showFinalNotification();
			}
			mProgressLog.delete();
		}

		private void removeNotification() {
			NOTIFICATION_MANAGER.cancel(INITIAL_ID);
		}

		private void showFinalNotification() {
			// Change text
			mBuilder = new NotificationCompat.Builder(VC.getAppContext());
			mBuilder.setContentTitle(Utils.getString(R.string.vc_finished))
					.setContentText(String.format(Utils
							.getString(R.string.format_click_to_open_video), mOutput.getName()))
					.setSmallIcon(R.drawable.ic_action_merge);

			// Open file on click
			Intent intent = new Intent();
			intent.setAction(android.content.Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(mOutput), VIDEO_MIME);
			PendingIntent pendingIntent = PendingIntent.getActivity(VC
					.getAppContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			mBuilder.setContentIntent(pendingIntent);

			// Notification removes itself after being used
			mBuilder.setAutoCancel(true);

			// ToDo notification sound
			// mBuilder.setSound(Uri.parse("file:///sdcard/notification/ringer.mp3"));

			// Show finished notification with a corresponding negative id
			NOTIFICATION_MANAGER.notify(-mTaskNum, mBuilder.build());

			// Cancel progress notification if the queue is empty
			if (--sIntentQueueSize <= 0) {
				stopForeground(true);
			}
		}

		/**
		 *
		 * @param outTime
		 * @return -1 on error
		 */
		private int outTimeToSeconds(final String outTime) {
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

		private String secondsToTime(int totalSeconds) {
			if (totalSeconds < 1) return null;
			int hours = totalSeconds / 3600;
			int minutes = (totalSeconds % 3600) / 60;
			int seconds = totalSeconds % 60;

			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}

	}

}
