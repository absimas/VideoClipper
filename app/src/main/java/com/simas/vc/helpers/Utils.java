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
package com.simas.vc.helpers;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.simas.vc.R;
import com.simas.vc.VC;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Helper methods
 */
public class Utils {

	private static final String TAG = "Utils";
	public static final String VIDEO_MIME = "video";
	private static final int IO_BUFFER_SIZE = 32768;

	public static class FlagContainer {

		private int mFlags;

		public int getFlags() {
			return mFlags;
		}

		/**
		 * @return true if flags have been modified
		 */
		public boolean addFlag(int flags) {
			return setFlags(flags, flags);
		}

		/**
		 * @return true if flags have been modified
		 */
		public boolean removeFlag(int flags) {
			return setFlags(0, flags);
		}

		/**
		 * @return true if flags have been modified
		 */
		private boolean setFlags(int flags, int mask) {
			int oldFlags = mFlags;
			mFlags = (mFlags & ~mask) | (flags & mask);
			return mFlags != oldFlags;
		}

	}

	/**
	 * Pipe bytes. Will close the streams after it's done.
	 * @param is    InputStream, will be closed
	 * @param os    OutputStream, will be closed
	 * @throws IOException
	 */
	public static void copyBytes(InputStream is, OutputStream os) throws IOException {
		byte[] buffer = new byte[IO_BUFFER_SIZE];
		int count;
		while ((count = is.read(buffer)) != -1) {
			os.write(buffer, 0, count);
		}
		is.close();
		os.close();
	}

	/**
	 *
	 * @param is
	 * @param destinationFile    must already exist
	 * @throws IOException
	 */
	public static void copyBytes(InputStream is, File destinationFile) throws IOException {
		OutputStream os = new FileOutputStream(destinationFile);
		copyBytes(is, os);
	}

	/**
	 * Will trim the file if it already exists
	 * @param bytes                  Byte array to be written to given file
	 * @param destionationFile    absolute path to destination file
	 */
	public static void copyBytes(byte[] bytes, File destionationFile) throws IOException {
		InputStream is = new ByteArrayInputStream(bytes);
		OutputStream os = new FileOutputStream(destionationFile);
		copyBytes(is, os);
	}

	/**
	 * Will use {@code destinationPath/assetFileName} as the output file
	 * @param assetName          Asset name
	 * @param destinationDir     Absolute path to the output directory
	 */
	public static void copyAsset(String assetName, String destinationDir)
			throws IOException {
		AssetManager assetManager = VC.getAppContext().getAssets();
		InputStream is = assetManager.open(assetName);
		File destinationFile = new File(destinationDir + File.separator + assetName);
		if (!destinationFile.exists()) {
			// If destinationDir creation failed or it already exists
			// AND
			// Failed to create a new file or it already exists =>
			// Throw because previous condition said it doesn't exist
			if (!new File(destinationDir).mkdirs() && !destinationFile.createNewFile()) {
				throw new IOException("The destination file doesn't exist and couldn't be" +
						"created! " + destinationFile.getPath());
			}
		}
		copyBytes(is, destinationFile);
	}

	/**
	 * Blocking method that will read InputStream to a string and return it
	 * @param is    InputStream from which data will be read
	 * @return null or a String containing the data that was read
	 * @throws IOException
	 */
	public static String readStream(InputStream is) throws IOException {
		BufferedReader reader;
		String output = "", line;
		reader = new BufferedReader(new InputStreamReader(is));
		while ((line = reader.readLine()) != null) {
			output += line + '\n';
		}

		is.close();
		reader.close();

		return TextUtils.isEmpty(output) ? null : output;
	}

	/**
	 * Converts density-independent pixels to pixels
	 * @param dp         density-independent pixels
	 * @return pixels representing the given dp value
	 */
	public static float dpToPx(float dp){
		DisplayMetrics metrics = VC.getAppResources().getDisplayMetrics();
		return dp * (metrics.densityDpi / 160f);
	}

	/**
	 * Converts pixels to density-independent pixels
	 * @param px         pixels
	 * @return dp representing the given pixel value
	 */
	public static float pxToDp(float px){
		DisplayMetrics metrics = VC.getAppResources().getDisplayMetrics();
		return px / (metrics.densityDpi / 160f);
	}

	public static float pxToSp(float px) {
		DisplayMetrics metrics = VC.getAppResources().getDisplayMetrics();
		return px / metrics.scaledDensity;
	}

	public static float spToPx(float sp) {
		DisplayMetrics metrics = VC.getAppResources().getDisplayMetrics();
		return sp * metrics.scaledDensity;
	}

	/**
	 * Fetches the given drawable from resources, converts it to a Bitmap and returns.
	 * @param resId      resource id representing the drawable, i.e. R.drawable.my_drawable
	 * @return a bitmap converted from a resource drawable
	 */
	public static Bitmap bitmapFromRes(int resId) {
		return BitmapFactory.decodeResource(VC.getAppResources(), resId);
	}

	/**
	 * Convenience method to fetch a string from a {@code JSONObject} or return null if it's not
	 * found.
	 * @param obj    JSONObject to look in
	 * @param key    String representing the key to look for
	 * @return String for the given key or null if it wasn't found
	 */
	public static String getJSONString(JSONObject obj, String key) {
		try {
			return obj.getString(key);
		} catch (JSONException e) {
			Log.w(TAG, "Error fetching a String!", e);
			return null;
		}
	}

	/**
	 * Convenience method to fetch a int from a {@code JSONObject} or return null if it's not
	 * found.
	 * @param obj    JSONObject to look in
	 * @param key    String representing the key to look for
	 * @return int for the given key or null if it wasn't found
	 */
	public static Integer getJSONInteger(JSONObject obj, String key) {
		try {
			return obj.getInt(key);
		} catch (JSONException e) {
			Log.w(TAG, "Error fetching an int!", e);
			return null;
		}
	}

	/**
	 * Convenience method to fetch a double from a {@code JSONObject} or return null if it's not
	 * found.
	 * @param obj    JSONObject to look in
	 * @param key    String representing the key to look for
	 * @return double for the given key or null if it wasn't found
	 */
	public static Double getJSONDouble(JSONObject obj, String key) {
		try {
			return obj.getDouble(key);
		} catch (JSONException e) {
			Log.w(TAG, "Error fetching double!", e);
			return null;
		}
	}

	/**
	 * Convenience method to fetch a long from a {@code JSONObject} or return null if it's not
	 * found.
	 * @param obj    JSONObject to look in
	 * @param key    String representing the key to look for
	 * @return long for the given key or null if it wasn't found
	 */
	public static Long getLong(JSONObject obj, String key) {
		try {
			return obj.getLong(key);
		} catch (JSONException e) {
			Log.w(TAG, "Error fetching long!", e);
			return null;
		}
	}

	public static void runOnUiThread(Runnable runnable) {
		new Handler(Looper.getMainLooper()).post(runnable);
	}

	public static int getNavigationBarHeight() {
		Resources resources = VC.getAppContext().getResources();
		int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
		if (resourceId > 0) {
			return resources.getDimensionPixelSize(resourceId);
		}
		return 0;
	}

	/**
	 * Converts seconds to a time string. Format hh:mm:ss
	 * @return string in the format of hh:mm:ss, 00:00:00 if given seconds are negative
	 */
	public static String secsToTime(int secs) {
		if (secs < 0) return "00:00:00";
		int hours = secs / 3600;
		int minutes = (secs % 3600) / 60;
		int seconds = secs % 60;

		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}


	/**
	 * Converts seconds to a time full time string. Format h hour(s) XX minute(s) XX second
	 * @return string in the format of hh:mm:ss, 00:00:00 if given seconds are negative
	 */
	public static String secsToFullTime(int secs) {
		if (secs < 0) return "0" + VC.getStr(R.string.second_short);
		int hours = secs / 3600;
		int minutes = (secs % 3600) / 60;
		int seconds = secs % 60;

		String output = "";
		if (hours > 0) {
			// If hours present, print minutes and seconds too
			output = String.format("%d%s %d%s ",
					hours, VC.getStr(R.string.hour_short),
					minutes, VC.getStr(R.string.minute_short));
		} else if (minutes > 0) {
			// If hours aren't present but minutes are, print them
			output = String.format("%d%s ", minutes, VC.getStr(R.string.minute_short));
		}
		// When not 0, seconds are always printed
		if (seconds > 0) {
			output += String.format("%d%s", seconds, VC.getStr(R.string.second_short));
		}

		return output;
	}

	public static Size getScreenSize() {
		DisplayMetrics metrics = VC.getAppResources().getDisplayMetrics();
		return new Size(metrics.widthPixels, metrics.heightPixels);
	}

	/**
	 * Convert bytes to megabytes with 2 digits after the decimal point and output as a string
	 */
	public static String bytesToMb(Long bytes) {
		if (bytes == null) bytes = 0L;
		double mb = bytes / 1024.0 / 1024.0;
		return String.format("%.2f %s", mb, VC.getStr(R.string.megabyte));
	}

	public static class Size {

		private int mWidth, mHeight;

		public Size(int width, int height) {
			mWidth = width;
			mHeight = height;
		}

		public int getWidth() {
			return mWidth;
		}

		public int getHeight() {
			return mHeight;
		}

	}

	/**
	 * Compares 2 {@code Object} references.
	 * @return false if the objects differ or either of them is {@code null}, otherwise true
	 */
	public static boolean equals(@Nullable Object obj1, @Nullable Object obj2) {
		if (obj1 == null || obj2 == null) return false;
		return obj1.equals(obj2);
	}

	public static Bitmap screenshot(View v) {
		v.setDrawingCacheEnabled(true);
		Bitmap b = Bitmap.createBitmap(v.getDrawingCache());
		v.setDrawingCacheEnabled(false);

		return b;
	}

	public static Bitmap screenshot2(View v) {
		Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(b);
		v.draw(canvas);

		return b;
	}

}
