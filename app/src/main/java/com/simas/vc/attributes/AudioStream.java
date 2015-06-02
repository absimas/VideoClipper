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
package com.simas.vc.attributes;

import com.simas.vc.VCException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class containing field values for a specific audio stream.
 * Required fields are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public class AudioStream extends Stream {

	public static final String FIELD_CHANNEL_COUNT = "Channel count";
	public static final String FIELD_SAMPLE_RATE = "Sample rate";

	/**
	 * Values are displayed to the user in a specific order. This order is saved in an ArrayList.
	 * Index 0 will be displayed first.
	 */
	public static final List<String> KEY_PRIORITIES = new ArrayList<String>() {{
		addAll(Stream.KEY_PRIORITIES);
		add(FIELD_DURATION);
		add(FIELD_CHANNEL_COUNT);
		add(FIELD_SAMPLE_RATE);
	}};

	public AudioStream(String codecName) throws VCException {
		super(codecName);
	}

	public Integer getChannelCount() {
		return (Integer) getValue(FIELD_CHANNEL_COUNT);
	}
	public Integer getSampleRate() {
		return (Integer) getValue(FIELD_SAMPLE_RATE);
	}

	public AudioStream setChannelCount(Integer channelCount) {
		setValue(FIELD_CHANNEL_COUNT, channelCount);
		return this;
	}
	public AudioStream setSampleRate(Integer sampleRate) {
		setValue(FIELD_CHANNEL_COUNT, sampleRate);
		return this;
	}

	@Override
	public List<String> getKeyPriorities() {
		return KEY_PRIORITIES;
	}

	@Override
	public String toString() {
		return String.format("%s\nAudioStream ChannelCount: %d, SampleRate: %d",
				super.toString(), getChannelCount(), getSampleRate());
	}

}
