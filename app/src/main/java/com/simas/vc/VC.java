package com.simas.vc;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

/**
 * Main application that is initialized when the JVM first starts an application.
 */
public class VC extends Application {

	private static Context mContext;

	static {
		System.loadLibrary("vc");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = getApplicationContext();
	}

	public static Context getAppContext() {
		return mContext;
	}

	public static Resources getAppResources() {
		return getAppContext().getResources();
	}

	public static AssetManager getAppAssets() {
		return getAppContext().getAssets();
	}

	public static String getStr(int resourceId) {
		return getAppResources().getString(resourceId);
	}

}
