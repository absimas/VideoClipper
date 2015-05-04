package com.simas.vc.background_tasks;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import com.simas.vc.ArgumentBuilder;
import com.simas.vc.Utils;
import com.simas.vc.VC;
import com.simas.vc.VCException;
import com.simas.wvc.R;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Feb 28.
 */

public class Ffmpeg {

	private static final String TAG = "ffmpeg";

	// C method prototypes
	public static native Bitmap createPreview(String videoPath);
	static native boolean cFfmpeg(String[] args);

	/**
	 *
	 * @param outputFile     output file (must already exist)
	 * @param sources        absolute path filenames to source videos that will be merged
	 * @param length         output length in seconds. If < 1, progress will be indeterminate
	 * @throws IOException  An un-recoverable, internal error
	 * @throws VCException An error message to be printed out for the user
	 */
	public static void concat(@NonNull File outputFile, @NonNull List<String> sources, int length)
			throws IOException, VCException {
		// Check source count
		if (sources.size() < 2) {
			throw new VCException(Utils.getString(R.string.at_least_2_videos));
		}

		// Prepare a tmp file with all video file names
		File tmpFile = File.createTempFile("vc", null);
		String sourceList = "";
		for (String source : sources) {
			sourceList += String.format("file '%s'\n", source);
		}
		Utils.copyBytes(sourceList.getBytes(), tmpFile);
		// Prepare a tmp file for progress output
		File progressFile = File.createTempFile("progress", null);

		// Prepare arguments
		String[] args = new ArgumentBuilder(TAG)
				.add("-y")                                   // Force overwrite output
				.add("-progress %s", progressFile.getPath()) // Output progress to tmp file
				.add("-f")                                   // Output to file
				.add("concat -i %s", tmpFile.getPath())      // Concat files listed in tmpFile
				.add("-c copy")                              // Copy source codecs
				.add("%s", outputFile.getPath())             // Output file
				.build();

		// Call service
		Context context = VC.getAppContext();
		Intent intent = new Intent(context, FfmpegService.class);
		intent.putExtra(FfmpegService.ARG_EXEC_ARGS, args);
		intent.putExtra(FfmpegService.ARG_OUTPUT_FILE, outputFile);
		intent.putExtra(FfmpegService.ARG_PROGRESS_FILE, progressFile);
		intent.putExtra(FfmpegService.ARG_OUTPUT_LENGTH, length);
		context.startService(intent);
	}

}

