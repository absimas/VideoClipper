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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import com.simas.vc.helpers.ArgumentBuilder;
import com.simas.vc.helpers.Utils;
import com.simas.vc.VC;
import com.simas.vc.VCException;
import com.simas.vc.R;
import com.simas.vc.attributes.VideoStream;
import com.simas.vc.nav_drawer.NavItem;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// ToDo rename concat to merge (including the action) or not?
// ToDo VCException use res instead of hardcoded string
// ToDo detect audio and video stream indexes and only then send them to the filters for processing

/**
 * Contains the convenience methods that might call {@code FFmpegService} to do furhter work via
 * JNI.
 */
public class FFmpeg {

	private static final String TAG = "ffmpeg";

	// C method prototypes
	public static native Bitmap createPreview(String videoPath);
	static native int cFFmpeg(String[] args);

	/**
	 *
	 * @param outputFile       output file (must already exist)
	 * @param items            items which will be concatenated
	 * @throws IOException An un-recoverable, internal error
	 * @throws VCException An error message to be printed out for the user
	 */
	public static void concat(@NonNull File outputFile, @NonNull List<NavItem> items)
			throws IOException, VCException {
		// Check source count
		if (items.size() < 2) {
			throw new VCException(Utils.getString(R.string.at_least_2_videos));
		}

		// Fetch item validity and calculate total duration
		int duration = 0;
		for (NavItem item : items) {
			if (item.getState() == NavItem.State.VALID) {
				duration += item.getAttributes().getDuration();
			} else {
				throw new VCException("Concatenation cancelled! Some items are invalid or are " +
						"still being processed.");
			}
		}

		// Prepare a tmp file for progress output
		File progressFile = File.createTempFile("vc-pg", null);

		// ToDo check stream counts

		boolean needsResizing = false, needsFiltering = false;
		VideoStream stream = null;
		// Loop items
		for (NavItem item : items) {
			// Loop streams
			for (VideoStream videoStream : item.getAttributes().getVideoStreams()) {
				if (stream == null) {
					stream = videoStream;
				} else {
					if (streamsNeedResizing(stream, videoStream)) {
						needsResizing = true;
					} else if (!streamsConcatenateableByDemuxing(stream, videoStream)) {
						needsFiltering = true;
					}
				}
			}
		}

		// Call the method based on the priorities. Resizing is the most important factor as it
		// will use a filter, other use a filter without resizing. The most basic concatenation
		// involves uses the concat demuxer.
		if (needsResizing) {
			concatFilterWithScaleAndPadding(outputFile, progressFile, items, duration);
		} else if (needsFiltering) {
			concatFilter(outputFile, progressFile, items, duration);
		} else {
			concatDemuxer(outputFile, progressFile, items, duration);
		}
	}

	private static void concatDemuxer(@NonNull File outputFile, @NonNull File progressFile,
	                                  @NonNull List<NavItem> items, int duration)
			throws IOException {
		/* Command
			./ffmpeg -y \
			-progress progress \
			-f concat \
			-auto_convert 1 \
			-i inputs \
			-codec copy \
			'output.mp4'
		 */

		// Prepare a tmp file with all video file names
		File tmpFile = File.createTempFile("vc-ls", null);
		String sourceList = "";
		for (NavItem item : items) {
			sourceList += String.format("file '%s'\n", item.getFile().getPath());
		}
		Utils.copyBytes(sourceList.getBytes(), tmpFile);

		// Prepare arguments
		String[] args = new ArgumentBuilder(TAG)
				.add("-y")                                  // Overwrite output file if it exists
				.add("-progress")                           // Output progress to tmp file
				.addSpaced("%s", progressFile.getPath())
				.add("-f concat")                           // Format concat
				.add("-auto_convert 1")                     // Convert packets to make streams concatenable
				.add("-i")                                  // Input files listed in tmpFile
				.addSpaced("%s", tmpFile.getPath())
				.add("-codec copy")                         // Copy source codecs
				.addSpaced("%s", outputFile.getPath())      // Output file
				.build();

		// Call service
		Context context = VC.getAppContext();
		Intent intent = new Intent(context, FFmpegService.class);
		intent.putExtra(FFmpegService.ARG_EXEC_ARGS, args);
		intent.putExtra(FFmpegService.ARG_OUTPUT_FILE, outputFile);
		intent.putExtra(FFmpegService.ARG_PROGRESS_FILE, progressFile);
		intent.putExtra(FFmpegService.ARG_OUTPUT_DURATION, duration);
		context.startService(intent);
	}

	private static void concatFilterWithScaleAndPadding(@NonNull File outputFile,
	                                                    @NonNull File progressFile,
	                                                    @NonNull List<NavItem> items, int duration)
			throws IOException {
		/* Command
			./ffmpeg -y \
			-progress progress \
			-i 'nature/goose.mp4' \
			-i 'nature/bee.mp4' \
			-filter_complex '\
			[0:0]scale=320:200[v1],[1:0]scale=320:200[v2],\
			[v1]pad=320:200:0:0[v1],[v2]pad=320:200:0:0[v2],\
			[v1][0:1][v2][1:1]concat=n=2:v=1:a=1[v][a]' \
			-map '[v]' -map '[a]' \
			'output.mp4'
		 */
		int itemCount = items.size();

		/* Output dimensions */
		int ow = 0;
		int oh = 0;
		// Use the biggest width and height from all items as output dimensions
		for (int i=0; i<itemCount; ++i) {
			NavItem item = items.get(i);
			VideoStream vs = item.getAttributes().getVideoStreams().get(0);
			if (vs.getWidth() > ow) ow = vs.getWidth();
			if (vs.getHeight() > oh) oh = vs.getHeight();
		}

		String[] inputs = new String[itemCount*2];
		String scaleFilters = "", padFilters = "", streams = "";
		for (int i=0; i<itemCount; ++i) {
			NavItem item = items.get(i);
			VideoStream vs = item.getAttributes().getVideoStreams().get(0);
			/* Input dimensions */
			int iw = vs.getWidth();
			int ih = vs.getHeight();

			// Inputs
			inputs[i*2] = "-i";
			inputs[i*2 + 1] = item.getFile().getPath();

			// If width and height already match the output's, then only set the SAR and save stream
			if (iw == ow && ih == oh) {
				// Streams // [i:video_stream] [i:audio_stream]
				streams += String.format("[%d:%d][%d:%d]", i, 0, i, 1);
			} else {
				/* Scale */
				// Use the bigger dimension and preserve the other
				int scaleW = -1, scaleH = -1;
				double modifier = ow / iw;
				if (ih * modifier > oh) {
					scaleH = oh;
				} else {
					scaleW = ow;
				}
				// (,)[i:streamID]scale=w:h[vi]
				scaleFilters += String.format("%s[%d:%d]scale=%d:%d[v%d]",
						(scaleFilters.isEmpty()) ? "" : ',', i, 0, scaleW, scaleH, i);

				// Pad // (,)[vi]pad=w:h:topx:topy[vi]
				padFilters += String.format("%s[v%d]pad=%d:%d:(%d-iw)/2:(%d-ih)/2[v%d]",
						(padFilters.isEmpty()) ? "" : ',', i, ow, oh, ow, oh, i);

				// Streams // [vi] [i:audio_stream]
				streams += String.format("[v%d][%d:%d]", i, i, 1);
			}
		}

		// Add a separator after each filter
		if (!scaleFilters.isEmpty()) scaleFilters += ",";
		if (!padFilters.isEmpty()) padFilters += ",";

		// ToDo avoid experimental codecs by using external libs (libfdk_aac is one of them)
		// Prepare arguments
		String[] args = new ArgumentBuilder(TAG)
				.add("-y")                                  // Overwrite output file if it exists
				.add("-strict experimental")                // Use experimental decoders
				/* Output progress to tmp file */
				.add("-progress")
				.addSpaced("%s", progressFile.getPath())
				.addSpaced(inputs)                          // List of sources
				/* Filters (scale,pad,setsar,concat) */
				.add("-filter_complex")
				.add("%s%s%sconcat=n=%d:v=1:a=1[v][a]",
						scaleFilters, padFilters, streams, itemCount)
				.add("-map [v] -map [a]")
				.add("-strict experimental")                // Use experimental encoders
				.addSpaced("%s", outputFile.getPath())      // Output file
				.build();

		Log.e(TAG, Arrays.toString(args));

		// Call service
		Context context = VC.getAppContext();
		Intent intent = new Intent(context, FFmpegService.class);
		intent.putExtra(FFmpegService.ARG_EXEC_ARGS, args);
		intent.putExtra(FFmpegService.ARG_OUTPUT_FILE, outputFile);
		intent.putExtra(FFmpegService.ARG_PROGRESS_FILE, progressFile);
		intent.putExtra(FFmpegService.ARG_OUTPUT_DURATION, duration);
		context.startService(intent);
	}

	/**
	 * This method expects the first stream is the video and the second one is audio. Ignores
	 * others.
	 */
	private static void concatFilter(@NonNull File outputFile, @NonNull File progressFile,
	                                  @NonNull List<NavItem> items, int duration)
			throws IOException {
		/* Command
			./ffmpeg -y \
			-progress progress \
			-i 'nature/goose.mp4' \
			-i 'nature/bee.mp4' \
			-filter_complex '[0:0] [0:1] [1:0] [1:1]  concat=n=2:v=1:a=1 [v] [a]' \
			-map '[v]' -map '[a]' \
			'output.mp4'
		 */
		// ToDo 0:0 0:1 fails if these streams don't match file's audio/video stream indexes

		int itemCount = items.size();
		String[] inputs = new String[itemCount*2];
		String streams = "";
		for (int i=0; i<itemCount; ++i) {
			NavItem item = items.get(i);
			inputs[i*2] = "-i";
			inputs[i*2 + 1] = item.getFile().getPath();

			streams += String.format("[%d:%d][%d:%d]", i, 0, i, 1);
			// Loop item streams
//			List<Stream> streams = item.getAttributes().getStreams();
//			int streamCount = streams.size();
//			for (int s=0; s<2; ++i) {
//
//			}
		}

		// ToDo avoid experimental codecs by using external libs (libfdk_aac is one of them)
		// Prepare arguments
		String[] args = new ArgumentBuilder(TAG)
				.add("-y")                                  // Overwrite output file if it exists
				.add("-strict experimental")                // Use experimental decoders
				.add("-progress")                           // Output progress to tmp file
				.addSpaced("%s", progressFile.getPath())
				.addSpaced(inputs)                          // List of sources
				/* Concat filter */
				.add("-filter_complex")
				.add("%sconcat=n=%d:v=1:a=1[v][a]", streams, itemCount)
				.add("-map [v] -map [a]")
				.add("-strict experimental")                // Use experimental encoders
				.addSpaced("%s", outputFile.getPath())      // Output file
				.build();

		// Call service
		Context context = VC.getAppContext();
		Intent intent = new Intent(context, FFmpegService.class);
		intent.putExtra(FFmpegService.ARG_EXEC_ARGS, args);
		intent.putExtra(FFmpegService.ARG_OUTPUT_FILE, outputFile);
		intent.putExtra(FFmpegService.ARG_PROGRESS_FILE, progressFile);
		intent.putExtra(FFmpegService.ARG_OUTPUT_DURATION, duration);
		context.startService(intent);
	}

	/**
	 * Assumes that {@code streamsNeedResizing} returns false for these streams.
	 * Check if the required fields match for both {@code VideoAttributes}. The required fields are:
	 * <ul>
	 *     <li>
	 *         Width
	 *     </li>
	 *     <li>
	 *         Height
	 *     </li>
	 *     <li>
	 *         Codec tag
	 *     </li>
	 *     <li>
	 *         TBN
	 *     </li>
	 *     <li>
	 *         TBC
	 *     </li>
	 *     <li>
	 *         TBR
	 *     </li>
	 * </ul>
	 * @return true if the required field set matches
	 */
	public static boolean streamsConcatenateableByDemuxing(VideoStream va1, VideoStream va2) {
		return Utils.equals(va1.getCodecTag(), va2.getCodecTag()) &&
				Utils.equals(va1.getTBN(), va2.getTBN()) &&
				Utils.equals(va1.getTBC(), va2.getTBC()) &&
				Utils.equals(va1.getTBR(), va2.getTBR());
	}

	/**
	 * Checks whether 2 {@code VideoStream}s need resizing.
	 * @return true if the 2 streams have different widths and/or heights
	 */
	public static boolean streamsNeedResizing(VideoStream va1, VideoStream va2) {
		return !Utils.equals(va1.getWidth(), va2.getWidth()) ||
				!Utils.equals(va1.getHeight(), va2.getHeight());
	}

}

