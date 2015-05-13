package com.simas.vc;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 14.
 */

/**
 * Custom argument builder. Strings passed to {@code add} will be split by string and parsed as
 * separate arguments. Quoted strings passed to {@code add} will be parsed as a single argument.
 */
public class ArgumentBuilder {

	List<String> mArgs = new ArrayList<>();

	public ArgumentBuilder(String execName) {
		add(execName);
	}

	/**
	 * Adds the given string as a full argument. It won't be split by spaces.
	 * @param arg    string argument that can include spaces
	 * @return {@code ArgumentBuilder}. Builder pattern.
	 */
	public ArgumentBuilder addSpaced(String arg) {
		mArgs.add(arg);
		return this;
	}

	public ArgumentBuilder addSpaced(String format, Object... args) {
		addSpaced(String.format(format, args));
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
		Log.e("TAG", Arrays.toString(args));
		return args;
	}

}
