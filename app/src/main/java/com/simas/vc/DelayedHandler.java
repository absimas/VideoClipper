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

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// ToDo each class that uses this, should specify when the delay ends.
	// They should specify it for every method and the variable in question

/**
 * Message Handler class that queues messages until {@code resume} is called.
 */
public class DelayedHandler {

	private final List<Runnable> mRunnableQueue = Collections
			.synchronizedList(new ArrayList<Runnable>());
	private boolean mResumed;
	private Handler mHandler;

	/**
	 * Create a resumable handler. Default state: Paused.
	 * @param handler    {@code Handler} to run the messages
	 */
	public DelayedHandler(Handler handler) {
		mHandler = handler;
	}

	/**
	 * Create a resumable handler. Default state: Paused.
	 */
	public DelayedHandler() {
		mHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Resume the handler.
	 */
	public final synchronized void resume() {
		mResumed = true;
		while (mRunnableQueue.size() > 0) {
			final Runnable runnable = mRunnableQueue.get(0);
			mRunnableQueue.remove(0);
			mHandler.post(runnable);
		}
	}

	/**
	 * Adds the Runnable to the queue. It will be run immediately if the handler is resumed.
	 */
	public void add(Runnable runnable) {
		if (mResumed) {
			mHandler.post(runnable);
		} else {
			mRunnableQueue.add(runnable);
		}
	}

}