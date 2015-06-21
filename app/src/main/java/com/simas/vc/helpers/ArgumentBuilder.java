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

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Custom argument builder. Strings passed to {@code add} will be split by string and parsed as
 * separate arguments. Quoted strings passed to {@code add} will be parsed as a single argument.
 * E.g. File names can have spaces in them therefore they should be added as a single parameter
 * with {@code addSpaced}.
 */
public class ArgumentBuilder {

	private final String TAG = getClass().getName();
	private final List<String> mArgs = new ArrayList<>();

	public ArgumentBuilder(String execName) {
		add(execName);
	}

	/**
	 * Adds the given string as a full argument. It won't be split by spaces.
	 * @param arg    spaced argument
	 */
	public ArgumentBuilder addSpaced(String arg) {
		mArgs.add(arg);
		return this;
	}

	public ArgumentBuilder addSpaced(String format, Object... args) {
		addSpaced(String.format(format, args));
		return this;
	}

	/**
	 * Adds multiple arguments to the builder without splitting any of them. Equivalent to
	 * multiple calls to {@code addSpaced(String)}.
	 * @param args    spaced argument list to be added
	 */
	public ArgumentBuilder addSpaced(String[] args) {
		Collections.addAll(mArgs, args);
		return this;
	}

	public ArgumentBuilder add(String args) {
		String[] splitArgs;
		splitArgs = args.split("\\s+");

		Collections.addAll(mArgs, splitArgs);
		return this;
	}

	public ArgumentBuilder add(String format, Object... args) {
		add(String.format(format, args));
		return this;
	}

	public String[] build() {
		String[] args = mArgs.toArray(new String[mArgs.size()]);
		Log.i(TAG, Arrays.toString(args));
		return args;
	}

}
