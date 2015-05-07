package com.simas.vc.background_tasks;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import com.simas.vc.VC;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.ArgumentBuilder;
import com.simas.vc.attributes.Attributes;
import com.simas.vc.attributes.AudioAttributes;
import com.simas.vc.R;
import com.simas.vc.Utils;
import com.simas.vc.attributes.VideoAttributes;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Feb 28.
 */

public class Ffprobe {

	private static final String TAG = "ffprobe";

	private static native boolean cFfprobe(String[] args, String outputPath);

	public static FileAttributes getFileAttributes(File inputFile, @NonNull VarRunnable onComplete)
			throws IOException, InterruptedException {

		if (!inputFile.exists()) throw new IOException("The input file doesn't exist!");

//		Invoke executable call
//		./ffprobe -v quiet -print_format json -show_format -show_streams -show_entries format=duration,size,format_name,format_long_name,filename,nb_streams -show_entries stream=codec_name,codec_long_name,codec_type,sample_rate,channels,duration,display_aspect_ratio,width,height 1.mp4

		File tmpFile = File.createTempFile("wvc-report", null);

		String[] args = new ArgumentBuilder(TAG)
				.add("-i %s", inputFile.getPath())          // Specify input file
				.add("-v quiet -print_format json")         // Output quietly in JSON
				// Format entries to show
				.add("-show_format -show_entries format=%s,%s,%s,%s,%s,%s",
						getStr(R.string.format_duration), getStr(R.string.format_size),
						getStr(R.string.format_name), getStr(R.string.format_long_name),
						getStr(R.string.format_filename), getStr(R.string.format_stream_count))
				// Stream entries to show
				.add("-show_streams -show_entries stream=%s,%s,%s,%s,%s,%s,%s,%s,%s",
						getStr(R.string.stream_name), getStr(R.string.stream_long_name),
						getStr(R.string.stream_type), getStr(R.string.stream_sample_rate),
						getStr(R.string.stream_channels), getStr(R.string.stream_duration),
						getStr(R.string.stream_aspect_ratio), getStr(R.string.stream_width),
						getStr(R.string.stream_height))
				.build();

		new FfprobeTask(onComplete, tmpFile).execute(args);


		return null;
	}

	private static class FfprobeTask extends AsyncTask<String, Void, Boolean> {

		private VarRunnable mSuccessRunnable;
		private File mTmpFile;

		public FfprobeTask(@NonNull VarRunnable successAction, @NonNull File tmpFile) {
			mSuccessRunnable = successAction;
			try {
				if (!tmpFile.exists()) throw new IOException();
				mTmpFile = tmpFile;
			} catch (IOException e) {
				throw new IllegalStateException("Temporary file couldn't be created!");
			}
		}

		@Override
		protected Boolean doInBackground(String... args) {
			if (!cFfprobe(args, mTmpFile.getPath())) {
				return false;
			}
			FileAttributes attributes = null;
			BufferedReader reader = null;
			try {
				// Parse file
				reader = new BufferedReader(new FileReader(mTmpFile));
				StringBuilder sb = new StringBuilder();
				String line = reader.readLine();

				while (line != null) {
					sb.append(line);
					sb.append('\n');
					line = reader.readLine();
				}
				String content = sb.toString();

				int firstOpeningBrace = content.indexOf('{');
				int lastClosingBrace = content.lastIndexOf('}');
				if (firstOpeningBrace == -1 || lastClosingBrace == -1) {
					return false;
				}
				String json = content.substring(firstOpeningBrace, lastClosingBrace+1);

				// Parse JSON
				attributes = parseJsonAttributes(json);
				Log.i(TAG, "FfprobeTask returned: " + attributes);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			// Update runnable
			if (mSuccessRunnable != null) {
				mSuccessRunnable.setVariable(attributes);
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			mSuccessRunnable.run();
		}

	}

	private static FileAttributes parseJsonAttributes(@NonNull String json) throws IOException {
		// Parse JSON
		List<JSONObject> streams = new ArrayList<>();
		JSONObject format;
		try {
			JSONObject obj = new JSONObject(json);
			// Fetch streams
			JSONArray streamArr = obj.getJSONArray("streams");
			int streamCount = streamArr.length();
			for (int i=0; i<streamCount; ++i) {
				streams.add(streamArr.getJSONObject(i));
			}
			// Fetch format
			format = obj.getJSONObject("format");
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}

		// Parse format (file/container)
		String fileName, formatName, formatLongName;
		Integer streamCount;
		Long size;
		Double duration;

		fileName = Utils.getString(format, getStr(R.string.format_filename));
		size = Utils.getLong(format, getStr(R.string.format_size));
		duration = Utils.getDouble(format, getStr(R.string.format_duration));
		formatName = Utils.getString(format, getStr(R.string.format_name));
		formatLongName = Utils.getString(format, getStr(R.string.format_long_name));
		streamCount = Utils.getInt(format, getStr(R.string.format_stream_count));

		if (streamCount == null || streamCount != streams.size()) {
			throw new IOException(String.format("Failed reading the JSON! Stream count" +
					" doesn't match: format: %d, streams: %d", streamCount, streams.size()));
		}

		FileAttributes fa = new FileAttributes();
		fa.setFileName(fileName);
		fa.setSize(size);
		fa.setDuration(duration);
		fa.setName(formatName);
		fa.setLongName(formatLongName);

		// Parse streams
		for (JSONObject stream : streams) {
			Attributes.Type type = getAttributeType(stream);
			if (type == null) continue;

			switch (type) {
				case AUDIO:
					AudioAttributes aa = new AudioAttributes();
					aa.setCodecName(Utils.getString(stream,
							getStr(R.string.stream_name)));
					aa.setCodecLongName(Utils.getString(stream,
							getStr(R.string.stream_long_name)));
					aa.setChannelCount(Utils.getInt(stream, getStr(R.string.stream_channels)));
					aa.setDuration(Utils.getDouble(stream, getStr(R.string.stream_duration)));
					aa.setSampleRate(Utils.getInt(stream, getStr(R.string.stream_sample_rate)));

					// ToDo require some fields before adding?
					fa.addStream(aa);
					break;
				case VIDEO:
					VideoAttributes va = new VideoAttributes();
					va.setCodecName(Utils.getString(stream, getStr(R.string.stream_name)));
					va.setCodecLongName(Utils.getString(stream, getStr(R.string.stream_long_name)));
					va.setAspectRatio(Utils.getString(stream, getStr(R.string.stream_aspect_ratio)));
					va.setSize(Utils.getInt(stream, getStr(R.string.stream_width)),
							Utils.getInt(stream, getStr(R.string.stream_height)));
					va.setDuration(Utils.getDouble(stream, getStr(R.string.stream_duration)));

					// Don't add the stream if any of the required fields not found
					if (va.getHeight() != null && va.getWidth() != null &&
							va.getCodecName() != null) {
						fa.addStream(va);
					}
					break;
				default:
					break;
			}
		}

		return fa;
	}

	private static Attributes.Type getAttributeType(JSONObject obj) {
		String attributeType = Utils.getString(obj, getStr(R.string.stream_type));
		if (attributeType != null) {
			Attributes.Type type;
			try {
				return Attributes.Type.valueOf(attributeType.toUpperCase());
			} catch (Exception e) {
				Log.w(TAG, "Unrecognized attribute type: " + attributeType);
			}
		}
		return null;
	}

	private static String getStr(int res) {
		return VC.getAppContext().getString(res);
	}

}
