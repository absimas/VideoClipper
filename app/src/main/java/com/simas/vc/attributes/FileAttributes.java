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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.simas.vc.R;
import com.simas.vc.VCException;
import com.simas.vc.helpers.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Class containing attribute values for a specific file.
 * Required attributes are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public class FileAttributes implements Parcelable {

	private String mFileName, mLongName, mName;
	private Long mSize = 0l;
	private List<AudioStream> mAudioStreams = new ArrayList<>();
	private List<VideoStream> mVideoStreams = new ArrayList<>();
	private List<Stream> mStreams = new ArrayList<>();
	private Double mDuration;

	public FileAttributes(String filename, Long size, Double duration) throws VCException {
		setFileName(filename);
		setSize(size);
		setDuration(duration);
	}

	public String getFileName() {
		return mFileName;
	}

	public String getLongName() {
		return mLongName;
	}

	public String getName() {
		return mName;
	}

	public Long getSize() {
		return mSize;
	}

	/**
	 * Duration in seconds
	 */
	public Double getDuration() {
		return mDuration;
	}

	public List<AudioStream> getAudioStreams() {
		return mAudioStreams;
	}

	public List<VideoStream> getVideoStreams() {
		return mVideoStreams;
	}

	public List<Stream> getStreams() {
		return mStreams;
	}


	public FileAttributes setName(String name) {
		mName = name;
		return this;
	}

	public FileAttributes setLongName(String longName) {
		mLongName = longName;
		return this;
	}

	public FileAttributes setFileName(String fileName) throws VCException {
		if (TextUtils.isEmpty(fileName)) {
			throw new VCException(Utils.getString(R.string.file_name_invalid));
		}
		mFileName = fileName;
		return this;
	}

	public FileAttributes setSize(Long size) throws VCException {
		if (size == null) throw new VCException(Utils.getString(R.string.file_size_invalid));
		mSize = size;
		return this;
	}

	public FileAttributes addStream(Stream stream) {
		// Combined streams to be able too look up by index
		mStreams.add(stream);
		if (stream instanceof AudioStream) {
			mAudioStreams.add((AudioStream) stream);
		} else {
			mVideoStreams.add((VideoStream) stream);
		}
		return this;
	}

	public FileAttributes setDuration(Double duration) throws VCException {
		if (duration == null) {
			throw new VCException(Utils.getString(R.string.file_duration_invalid));
		}
		mDuration = duration;
		return this;
	}

	@Override
	public String toString() {
		return String.format("FileAttributes FileName: %s, LongName: %s, Name: %s, Size: %d",
				getFileName(), getLongName(), getName(), getSize());
	}

	/* Parcelable */
	public void writeToParcel(Parcel out, int flags) {
		out.writeValue(mFileName);
		out.writeValue(mLongName);
		out.writeValue(mName);
		out.writeValue(mSize);
		out.writeList(mAudioStreams);
		out.writeList(mVideoStreams);
		out.writeList(mStreams);
	}

	public static final Parcelable.Creator<FileAttributes> CREATOR
			= new Parcelable.Creator<FileAttributes>() {
		public FileAttributes createFromParcel(Parcel in) {
			return new FileAttributes(in);
		}

		public FileAttributes[] newArray(int size) {
			return new FileAttributes[size];
		}
	};

	private FileAttributes(Parcel in) {
		mFileName = (String) in.readValue(String.class.getClassLoader());
		mLongName = (String) in.readValue(String.class.getClassLoader());
		mName = (String) in.readValue(String.class.getClassLoader());
		mSize = (Long) in.readValue(Long.class.getClassLoader());
		// Audio
		mAudioStreams = new ArrayList<>();
		in.readList(mAudioStreams, AudioStream.class.getClassLoader());
		// Video
		mVideoStreams = new ArrayList<>();
		in.readList(mVideoStreams, VideoStream.class.getClassLoader());
		// Combined, indexed
		mStreams = new ArrayList<>();
		in.readList(mStreams, Stream.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}
