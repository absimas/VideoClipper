package com.simas.vc;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
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
 * Created by Simas Abramovas on 2015 Feb 28.
 */

public class Utils {

	private static final String TAG = "Utils";
	public static final String VIDEO_MIME = "video";
	private static final int IO_BUFFER_SIZE = 32768;

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
	public static String getString(JSONObject obj, String key) {
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
	public static Integer getInt(JSONObject obj, String key) {
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
	public static Double getDouble(JSONObject obj, String key) {
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

	/**
	 * Convenience method to return a string associated with the applications context and the
	 * given resource id.
	 * @param resourceId    resource id that the string is associated with
	 * @return string associated with the given resourceId and the application's context
	 */
	public static String getString(int resourceId) {
		return VC.getAppContext().getString(resourceId);
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

}
