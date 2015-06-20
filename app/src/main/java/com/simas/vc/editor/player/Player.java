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
package com.simas.vc.editor.player;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Notes:
 * - Do not call {@link #setOnPreparedListener(OnPreparedListener)} and
 * {@link #setOnErrorListener(OnErrorListener)}. Instead use
 * {@link #addOnPreparedListener(OnPreparedListener)} and
 * {@link #addOnErrorListener(OnErrorListener)} accordingly.
 */
public class Player extends MediaPlayer implements MediaPlayer.OnPreparedListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

	/**
	 * @see
	 * <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#StateDiagram">
	 *     MediaPlayer</a>
	 */
	public enum State {
		ERROR, RELEASED, IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, STOPPED, PAUSED
	}

	private State mState = State.IDLE;
	private final String TAG = getClass().getName();
	private final CopyOnWriteArraySet<OnPreparedListener> mPreparedListeners =
			new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<OnErrorListener> mErrorListeners = new CopyOnWriteArraySet<>();
	private Controls mControls;
	private OnStateChangedListener mStateListener;

	public Player() {
		setAudioStreamType(AudioManager.STREAM_MUSIC);
		super.setOnPreparedListener(this);
		super.setOnErrorListener(this);
		setOnCompletionListener(this);
	}

	private synchronized void setState(State newState) {
		final State previousState = mState;
		mState = newState;
		if (mStateListener != null) {
			mStateListener.onStateChanged(previousState, newState);
		}
	}

	public synchronized State getState() {
		return mState;
	}

	@Override
	public synchronized void start() throws IllegalStateException {
		super.start();
		setState(State.STARTED);
		if (getControls() != null) {
			getControls().setPlaying(true);
		}
	}

	@Override
	public synchronized void stop() throws IllegalStateException {
		super.stop();
		setState(State.STOPPED);
		if (getControls() != null) {
			getControls().setPlaying(false);
		}
	}

	@Override
	public synchronized void pause() throws IllegalStateException {
		super.pause();
		setState(State.PAUSED);
		if (getControls() != null) {
			getControls().setPlaying(false);
		}
	}

	@Override
	public synchronized void release() {
		super.release();
		setState(State.RELEASED);
		if (getControls() != null) {
			getControls().setPlaying(false);
		}
	}

	private Object mDataSource;

	@Override
	public synchronized void setDataSource(Context context, Uri uri)
			throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
		super.setDataSource(context, uri);
		mDataSource = uri;
		setState(State.INITIALIZED);
	}

	@Override
	public synchronized void setDataSource(FileDescriptor fd)
			throws IOException, IllegalArgumentException, IllegalStateException {
		super.setDataSource(fd);
		mDataSource = fd;
		setState(State.INITIALIZED);
	}

	@Override
	public synchronized void setDataSource(String path)
			throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
		super.setDataSource(path);
		mDataSource = path;
		setState(State.INITIALIZED);
	}

	@Override
	public synchronized void setDataSource(FileDescriptor fd, long offset, long length)
			throws IOException, IllegalArgumentException, IllegalStateException {
		super.setDataSource(fd, offset, length);
		mDataSource = fd;
		setState(State.INITIALIZED);
	}

	@Override
	public synchronized void setDataSource(Context context, Uri uri, Map<String, String> headers)
			throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
		super.setDataSource(context, uri, headers);
		mDataSource = uri;
		setState(State.INITIALIZED);
	}

	public Object getDataSource() {
		return mDataSource;
	}

	@Override
	public synchronized void reset() {
		super.reset();
		setState(State.IDLE);
		if (getControls() != null) {
			getControls().setPlaying(false);
		}
	}

	@Override
	public synchronized void seekTo(int msec) throws IllegalStateException {
		super.seekTo(msec);
		if (getControls() != null) {
			getControls().setCurrent(msec);
		}
	}

	@Override
	public synchronized void onPrepared(MediaPlayer mp) {
		setState(State.PREPARED);

		for (OnPreparedListener listener : mPreparedListeners) {
			listener.onPrepared(mp);
		}
	}

	/**
	 * Stops playing. Updates current and total times.
	 * If no controls are connected to the player, does nothing.
	 */
	public synchronized void updateControls() {
		if (getControls() != null) {
			getControls().setCurrent(0);
			switch (getState()) {
				case ERROR: case IDLE: case INITIALIZED:
					break;
				default:
					getControls().setDuration(getDuration());
			}
		}
	}

	@Override
	public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
		setState(State.ERROR);
		for (OnErrorListener listener : mErrorListeners) {
			listener.onError(mp, what, extra);
		}
		return false;
	}

	@Override
	public synchronized void prepareAsync() throws IllegalStateException {
		setState(State.PREPARING);
		super.prepareAsync();
	}

	@Override
	public synchronized void prepare() throws IOException, IllegalStateException {
		setState(State.PREPARING);
		super.prepare();
	}

	@Override
	public synchronized void onCompletion(MediaPlayer mp) {
		setState(State.PAUSED);
		if (getControls() != null) {
			getControls().setPlaying(false);
		}
	}

	public void setControls(Controls controls) {
		mControls = controls;
	}

	public Controls getControls() {
		return mControls;
	}

	public void addOnPreparedListener(OnPreparedListener listener) {
		mPreparedListeners.add(listener);
	}

	public void removeOnPreparedListener(OnPreparedListener listener) {
		mPreparedListeners.remove(listener);
	}

	public void addOnErrorListener(OnErrorListener listener) {
		mErrorListeners.add(listener);
	}

	public void removeOnErrorListeners() {
		mErrorListeners.clear();
	}

	public void removeOnPreparedListeners() {
		mPreparedListeners.clear();
	}

	public void removeOnErrorListener(OnErrorListener listener) {
		mErrorListeners.remove(listener);
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		throw new IllegalStateException("Player cannot have its prepared listener set! Use " +
				"addOnPreparedListener instead.");
	}

	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		throw new IllegalStateException("Player cannot have its error listener set! Use " +
				"addOnErrorListener instead.");
	}

	public void setOnStateChangedListener(OnStateChangedListener listener) {
		mStateListener = listener;
	}

	public interface OnStateChangedListener {
		void onStateChanged(@Nullable State previousState, @NonNull State newState);
	}

}