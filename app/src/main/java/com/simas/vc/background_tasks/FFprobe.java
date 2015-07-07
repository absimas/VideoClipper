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

import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import com.simas.vc.VCException;
import com.simas.vc.attributes.AudioStream;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.helpers.ArgumentBuilder;
import com.simas.vc.attributes.Stream;
import com.simas.vc.R;
import com.simas.vc.attributes.VideoStream;
import com.simas.vc.helpers.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.simas.vc.helpers.Utils.*;

/**
 * Connects to FFprobe library via JNI inside of a separate process.
 */
public class FFprobe {

	private static final String TAG = "ffprobe";
	private static final List<String> INVALID_CODEC_NAMES = new ArrayList<String>() {
		{
			add("mjpeg");
		}
	};

	private static native int cFFprobe(String[] args, String outputPath);

	/**
	 * This operation is synchronous and cannot be run on the UI thread.
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public synchronized static FileAttributes parseAttributes(File inputFile) throws VCException {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			throw new IllegalStateException("parseAttributes cannot be run on the UI thread!");
		}

		/* Executable call used
			./ffprobe -i 'nature/bee.mp4' \
			-v quiet -print_format json \
			-show_format -show_entries format=duration,size,format_name,format_long_name,filename,nb_streams \
			-show_streams -show_entries stream=codec_name,codec_long_name,codec_type,sample_rate,channels,duration,display_aspect_ratio,width,height,time_base,codec_time_base,r_frame_rate
		*/

		// Check if input exists
		if (!inputFile.exists()) {
			throw new VCException(Utils.getString(R.string.input_not_found));
		}

		// Create a temporary file to hold the stdout output
		File tmpFile;
		try {
			tmpFile = File.createTempFile("vc-out", null);
			tmpFile.delete();
			tmpFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			throw new VCException(Utils.getString(R.string.tmp_not_created));
		}

		// Create arguments for ffprobe
		final String[] args = new ArgumentBuilder(TAG)
				.add("-i")
				.addSpaced("%s", inputFile.getPath())   // Spaced input file path
				.add("-v quiet -print_format json")     // Output quietly in JSON
						// Format entries to show
				.add("-show_format -show_entries format=%s,%s,%s,%s,%s,%s",
						Utils.getString(R.string.format_duration),
						Utils.getString(R.string.format_size),
						Utils.getString(R.string.format_name),
						Utils.getString(R.string.format_long_name),
						Utils.getString(R.string.format_filename),
						Utils.getString(R.string.format_stream_count))
						// Stream entries to show
				.add("-show_streams -show_entries stream=%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
						Utils.getString(R.string.stream_name),
						Utils.getString(R.string.stream_long_name),
						Utils.getString(R.string.stream_type),
						Utils.getString(R.string.stream_sample_rate),
						Utils.getString(R.string.stream_channels),
						Utils.getString(R.string.stream_duration),
						Utils.getString(R.string.stream_aspect_ratio),
						Utils.getString(R.string.stream_width),
						Utils.getString(R.string.stream_height),
						Utils.getString(R.string.stream_tbn),
						Utils.getString(R.string.stream_tbc),
						Utils.getString(R.string.stream_tbr),
						Utils.getString(R.string.stream_codec_tag))
				.build();

		if (cFFprobe(args, tmpFile.getPath()) != 0) {
			throw new VCException(Utils.getString(R.string.ffprobe_fail));
		}

		BufferedReader reader = null;
		FileAttributes fa = null;
		try {
			// Parse file
			reader = new BufferedReader(new FileReader(tmpFile));
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
				return null;
			}
			String json = content.substring(firstOpeningBrace, lastClosingBrace+1);

			// Parse JSON
			fa = parseJsonAttributes(json);
			Log.i(TAG, "Parsed attributes: " + fa);
		} catch (IOException e) {
			e.printStackTrace();
			throw new VCException(Utils.getString(R.string.ffprobe_fail));
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// Delete the tmp file
		tmpFile.delete();

		// No support for files with no audio/video streams (for now?)
		if (fa != null) {
			if (fa.getAudioStreams().size() == 0) {
				throw new VCException(Utils.getString(R.string.audioless_unsupported));
			} else if (fa.getVideoStreams().size() == 0) {
				throw new VCException(Utils.getString(R.string.videoless_unsupported));
			}
		}

		return fa;
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
		FileAttributes fa = new FileAttributes(
				getJSONString(format, Utils.getString(R.string.format_filename)),
				getLong(format, Utils.getString(R.string.format_size)),
				getJSONDouble(format, Utils.getString(R.string.format_duration)));
		fa.setName(getJSONString(format, Utils.getString(R.string.format_name)))
				.setLongName(getJSONString(format, Utils.getString(R.string.format_long_name)));

		// Streams
		for (JSONObject jsonObj : jStream) {
			StreamType streamType = getStreamType(jsonObj);
			if (streamType == null) continue;

			// Stream
			Stream stream;

			// Codec name
			String codecName = getJSONString(jsonObj, Utils.getString(R.string.stream_name));
			if (codecName == null || INVALID_CODEC_NAMES.contains(codecName.toLowerCase())) {
				Log.w(TAG, "Skipped invalid codec: " + codecName);
				continue;
			}

			switch (streamType) {
				case AUDIO:
					stream = new AudioStream(codecName)
							.setChannelCount(getJSONInteger(jsonObj,
									Utils.getString(R.string.stream_channels)))
							.setSampleRate(getJSONInteger(jsonObj,
									Utils.getString(R.string.stream_sample_rate)));
					break;
				case VIDEO:
					// Create VideoStream
					Integer w = getJSONInteger(jsonObj, Utils.getString(R.string.stream_width));
					Integer h = getJSONInteger(jsonObj, Utils.getString(R.string.stream_height));

					stream = new VideoStream(w, h, codecName)
							.setAspectRatio(getJSONString(jsonObj,
									Utils.getString(R.string.stream_aspect_ratio)))
							.setTBN(getJSONString(jsonObj, Utils.getString(R.string.stream_tbn)))
							.setTBC(getJSONString(jsonObj, Utils.getString(R.string.stream_tbc)))
							.setTBR(getJSONString(jsonObj, Utils.getString(R.string.stream_tbr)));
					break;
				default:
					continue;
			}

			/* Shared stream attributes */
			// Codec name
			stream.setCodecName(codecName);

			// Stream duration
			Double duration = getJSONDouble(jsonObj, Utils.getString(R.string.stream_duration));

			// Codec tag (default is 0)
			String tag = getJSONString(jsonObj, Utils.getString(R.string.stream_codec_tag));
			try {
				stream.setCodecTag(Integer.decode(tag));
			} catch (NumberFormatException e) {
				e.printStackTrace();
//				throw new VCException("Unrecognized codec found!");
			}

			// Codec long name
			stream.setCodecLongName(getJSONString(jsonObj,
					Utils.getString(R.string.stream_long_name)));

			// Append to FileAttributes
			fa.addStream(stream);
		}

		return fa;
	}

	private static StreamType getStreamType(JSONObject obj) {
		String attributeType = getJSONString(obj, Utils.getString(R.string.stream_type));
		if (attributeType != null) {
			StreamType streamType;
			try {
				return StreamType.valueOf(attributeType.toUpperCase());
			} catch (Exception e) {
				Log.w(TAG, "Unrecognized attribute type: " + attributeType);
			}
		}
		return null;
	}

	enum StreamType {
		AUDIO, VIDEO
	}

}
