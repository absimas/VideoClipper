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
package com.simas.vc;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

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
