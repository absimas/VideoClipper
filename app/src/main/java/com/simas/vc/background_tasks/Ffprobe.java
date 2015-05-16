package com.simas.vc.background_tasks;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import com.simas.vc.VC;
import com.simas.vc.VCException;
import com.simas.vc.attributes.AudioStream;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.ArgumentBuilder;
import com.simas.vc.attributes.Stream;
import com.simas.vc.R;
import com.simas.vc.attributes.VideoStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.simas.vc.Utils.*;

/**
 * Created by Simas Abramovas on 2015 Feb 28.
 */

// ToDo IntentService so consequent calls to ffprobe are queued.

public class Ffprobe {

	private static final String TAG = "ffprobe";

	private static native boolean cFfprobe(String[] args, String outputPath);

	public static void parseAttributes(File inputFile, @NonNull VarRunnable onComplete)
			throws IOException, InterruptedException {

		if (!inputFile.exists()) throw new IOException("The input file doesn't exist!");

		/* Executable call used // ToDo gal pretty naudot? td hh:mm:ss:ms...
			./ffprobe -i 'nature/bee.mp4' \
			-v quiet -print_format json \
			-show_format -show_entries format=duration,size,format_name,format_long_name,filename,nb_streams \
			-show_streams -show_entries stream=codec_name,codec_long_name,codec_type,sample_rate,channels,duration,display_aspect_ratio,width,height,time_base,codec_time_base,r_frame_rate
		*/

		File tmpFile = File.createTempFile("vc-rp", null);
		Log.e(TAG, "Tmp: " + tmpFile.getPath());

		String[] args = new ArgumentBuilder(TAG)
				.add("-i")
				.addSpaced("%s", inputFile.getPath())   // Spaced input file path
				.add("-v quiet -print_format json")     // Output quietly in JSON
				// Format entries to show
				.add("-show_format -show_entries format=%s,%s,%s,%s,%s,%s",
						getStr(R.string.format_duration), getStr(R.string.format_size),
						getStr(R.string.format_name), getStr(R.string.format_long_name),
						getStr(R.string.format_filename), getStr(R.string.format_stream_count))
						// Stream entries to show
				.add("-show_streams -show_entries stream=%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
						getStr(R.string.stream_name), getStr(R.string.stream_long_name),
						getStr(R.string.stream_type), getStr(R.string.stream_sample_rate),
						getStr(R.string.stream_channels), getStr(R.string.stream_duration),
						getStr(R.string.stream_aspect_ratio), getStr(R.string.stream_width),
						getStr(R.string.stream_height), getStr(R.string.stream_tbn),
						getStr(R.string.stream_tbc), getStr(R.string.stream_tbr),
						getStr(R.string.stream_codec_tag))
				.build();

		new FfprobeTask(onComplete, tmpFile).execute(args);
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
			FileAttributes fa = null;
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
				fa = parseJsonAttributes(json);
				Log.i(TAG, "FfprobeTask returned: " + fa);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			} catch (VCException e) {
				e.printStackTrace();
				// ToDo display error to the user!
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
				mSuccessRunnable.setVariable(fa);
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			mSuccessRunnable.run();
		}

	}

	private static FileAttributes parseJsonAttributes(@NonNull String json)
			throws IOException, VCException {
		Log.d(TAG, json);
		// Parse JSON
		List<JSONObject> jStream = new ArrayList<>();
		JSONObject format;
		try {
			JSONObject obj = new JSONObject(json);
			// Fetch streams
			JSONArray streamArr = obj.getJSONArray("streams");
			int streamCount = streamArr.length();
			for (int i=0; i<streamCount; ++i) {
				jStream.add(streamArr.getJSONObject(i));
			}
			// Fetch format
			format = obj.getJSONObject("format");
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}

		// Format
		FileAttributes fa = new FileAttributes();
		fa.setFileName(getString(format, getStr(R.string.format_filename)))
				.setSize(getLong(format, getStr(R.string.format_size)))
				.setName(getString(format, getStr(R.string.format_name)))
				.setLongName(getString(format, getStr(R.string.format_long_name)))
				.setDuration(getDouble(format, getStr(R.string.format_duration)));

		// Streams
		for (JSONObject jObj : jStream) {
			Stream.Type type = getStreamType(jObj);
			if (type == null) continue;

			// Stream
			Stream stream;

			// Codec name
			String codecName = getString(jObj, getStr(R.string.stream_name));

			switch (type) {
				case AUDIO:
					stream = new AudioStream(codecName)
							.setChannelCount(getInt(jObj, getStr(R.string.stream_channels)))
							.setSampleRate(getInt(jObj, getStr(R.string.stream_sample_rate)));
					break;
				case VIDEO:
					// Create VideoStream
					Integer width = getInt(jObj, getStr(R.string.stream_width));
					Integer height = getInt(jObj, getStr(R.string.stream_height));

					stream = new VideoStream(width, height, codecName)
							.setAspectRatio(getString(jObj, getStr(R.string.stream_aspect_ratio)))
							.setTBN(getString(jObj, getStr(R.string.stream_tbn)))
							.setTBC(getString(jObj, getStr(R.string.stream_tbc)))
							.setTBR(getString(jObj, getStr(R.string.stream_tbr)));
					break;
				default:
					continue;
			}

			/* Shared stream attributes */
			// Codec name
			stream.setCodecName(codecName);

			// Stream duration
			Double duration = getDouble(jObj, getStr(R.string.stream_duration));

			// Codec tag (default is 0)
			String tag = getString(jObj, getStr(R.string.stream_codec_tag));
			try {
				stream.setCodecTag(Integer.decode(tag));
			} catch (NumberFormatException e) {
				e.printStackTrace();
				throw new VCException("Unrecognized codec found!");
			}

			// Codec long name
			stream.setCodecLongName(getString(jObj, getStr(R.string.stream_long_name)));

			// Append to FileAttributes
			fa.addStream(stream);
		}

		return fa;
	}

	private static Stream.Type getStreamType(JSONObject obj) {
		String attributeType = getString(obj, getStr(R.string.stream_type));
		if (attributeType != null) {
			Stream.Type type;
			try {
				return Stream.Type.valueOf(attributeType.toUpperCase());
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
