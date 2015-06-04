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
 * Connects to FFprobe library via JNI inside of a separate process.
 */
public class Ffprobe {

	private static final String TAG = "ffprobe";

	private static native int cFfprobe(String[] args, String outputPath);

	/**
	 * This operation is synchronous and cannot be run on the UI thread.
	 */
	public synchronized static FileAttributes parseAttributes(File inputFile) throws VCException {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			throw new IllegalStateException("parseAttributes cannot be run on the UI thread!");
		}

		/* Executable call used // ToDo gal pretty naudot? td hh:mm:ss:ms...
			./ffprobe -i 'nature/bee.mp4' \
			-v quiet -print_format json \
			-show_format -show_entries format=duration,size,format_name,format_long_name,filename,nb_streams \
			-show_streams -show_entries stream=codec_name,codec_long_name,codec_type,sample_rate,channels,duration,display_aspect_ratio,width,height,time_base,codec_time_base,r_frame_rate
		*/

		// Check if input exists
		if (!inputFile.exists()) {
			throw new VCException("Input file doesn't exist!");
		}

		// Create a temporary file to hold the stdout output
		File tmpFile;
		try {
//			tmpFile = File.createTempFile("vc-out", null);
			tmpFile = new File("/sdcard/Movies/test.txt");
			tmpFile.delete();
			tmpFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			throw new VCException("Temporary file couldn't be created! Please try again.");
		}

		// Create arguments for ffprobe
		final String[] args = new ArgumentBuilder(TAG)
				.add("-i")
				.addSpaced("%s", inputFile.getPath())   // Spaced input file path
				.add("-v quiet -print_format json")     // Output quietly in JSON
						// Format entries to show
				.add("-show_format -show_entries format=%s,%s,%s,%s,%s,%s",
						VC.getStr(R.string.format_duration), VC.getStr(R.string.format_size),
						VC.getStr(R.string.format_name), VC.getStr(R.string.format_long_name),
						VC.getStr(R.string.format_filename),
						VC.getStr(R.string.format_stream_count))
						// Stream entries to show
				.add("-show_streams -show_entries stream=%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
						VC.getStr(R.string.stream_name), VC.getStr(R.string.stream_long_name),
						VC.getStr(R.string.stream_type), VC.getStr(R.string.stream_sample_rate),
						VC.getStr(R.string.stream_channels), VC.getStr(R.string.stream_duration),
						VC.getStr(R.string.stream_aspect_ratio), VC.getStr(R.string.stream_width),
						VC.getStr(R.string.stream_height), VC.getStr(R.string.stream_tbn),
						VC.getStr(R.string.stream_tbc), VC.getStr(R.string.stream_tbr),
						VC.getStr(R.string.stream_codec_tag))
				.build();

		if (cFfprobe(args, tmpFile.getPath()) != 0) {
			throw new VCException(VC.getStr(R.string.ffprobe_fail));
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
			throw new VCException(VC.getStr(R.string.ffprobe_fail));
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
				getJSONString(format, VC.getStr(R.string.format_filename)),
				getLong(format, VC.getStr(R.string.format_size)),
				getJSONDouble(format, VC.getStr(R.string.format_duration)));
		fa.setName(getJSONString(format, VC.getStr(R.string.format_name)))
				.setLongName(getJSONString(format, VC.getStr(R.string.format_long_name)));

		// Streams
		for (JSONObject jsonObj : jStream) {
			StreamType streamType = getStreamType(jsonObj);
			if (streamType == null) continue;

			// Stream
			Stream stream;

			// Codec name
			String codecName = getJSONString(jsonObj, VC.getStr(R.string.stream_name));

			switch (streamType) {
				case AUDIO:
					stream = new AudioStream(codecName)
							.setChannelCount(getJSONInteger(jsonObj,
									VC.getStr(R.string.stream_channels)))
							.setSampleRate(getJSONInteger(jsonObj,
									VC.getStr(R.string.stream_sample_rate)));
					break;
				case VIDEO:
					// Create VideoStream
					Integer width = getJSONInteger(jsonObj, VC.getStr(R.string.stream_width));
					Integer height = getJSONInteger(jsonObj, VC.getStr(R.string.stream_height));

					stream = new VideoStream(width, height, codecName)
							.setAspectRatio(getJSONString(jsonObj,
									VC.getStr(R.string.stream_aspect_ratio)))
							.setTBN(getJSONString(jsonObj, VC.getStr(R.string.stream_tbn)))
							.setTBC(getJSONString(jsonObj, VC.getStr(R.string.stream_tbc)))
							.setTBR(getJSONString(jsonObj, VC.getStr(R.string.stream_tbr)));
					break;
				default:
					continue;
			}

			/* Shared stream attributes */
			// Codec name
			stream.setCodecName(codecName);

			// Stream duration
			Double duration = getJSONDouble(jsonObj, VC.getStr(R.string.stream_duration));

			// Codec tag (default is 0)
			String tag = getJSONString(jsonObj, VC.getStr(R.string.stream_codec_tag));
			try {
				stream.setCodecTag(Integer.decode(tag));
			} catch (NumberFormatException e) {
				e.printStackTrace();
//				throw new VCException("Unrecognized codec found!");
			}

			// Codec long name
			stream.setCodecLongName(getJSONString(jsonObj, VC.getStr(R.string.stream_long_name)));

			// Append to FileAttributes
			fa.addStream(stream);
		}

		return fa;
	}

	private static StreamType getStreamType(JSONObject obj) {
		String attributeType = getJSONString(obj, VC.getStr(R.string.stream_type));
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
