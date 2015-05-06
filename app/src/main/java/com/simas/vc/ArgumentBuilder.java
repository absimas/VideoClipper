package com.simas.vc;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 14.
 */

// ToDo will fail for file names with spaces!!!

public class ArgumentBuilder {

	List<String> mArgs = new ArrayList<>();

	public ArgumentBuilder(String execName) {
		add(execName);
	}

	public ArgumentBuilder add(String args) {
		String[] splitArgs = args.split("\\s+");
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
