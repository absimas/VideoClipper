package com.simas.vc;

/**
 * Created by Simas Abramovas on 2015 May 03.
 */

import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Message Handler class that supports buffering up of messages when the activity is paused i.e. in the background.
 */
public class ResumableHandler {

	private final List<Runnable> mRunnableQueue = Collections
			.synchronizedList(new ArrayList<Runnable>());
	private boolean mResumed;
	private Handler mHandler;

	/**
	 * Create a resumable handler. Default state: Paused.
	 * @param handler    {@code Handler} to run the messages
	 */
	public ResumableHandler(Handler handler) {
		mHandler = handler;
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
	 * Adds the Runnable to the queue. It will be run immediatelly if the handler is resumed.
	 */
	public void add(Runnable runnable) {
		if (mResumed) {
			mHandler.post(runnable);
		} else {
			mRunnableQueue.add(runnable);
		}
	}

}