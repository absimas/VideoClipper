package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

public class FileAttributes implements Parcelable {

	private String mFileName, mLongName, mName;
	private Long mSize = 0l;
	private List<AudioStream> mAudioStreams = new ArrayList<>();
	private List<VideoStream> mVideoStreams = new ArrayList<>();
	private Double mDuration;

	public FileAttributes() {}

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


	public FileAttributes setName(String name) {
		this.mName = name;
		return this;
	}

	public FileAttributes setLongName(String longName) {
		this.mLongName = longName;
		return this;
	}

	public FileAttributes setFileName(String fileName) {
		this.mFileName = fileName;
		return this;
	}

	public FileAttributes setSize(Long size) {
		this.mSize = size;
		return this;
	}

	public FileAttributes addStream(Stream stream) {
		if (stream instanceof AudioStream) {
			mAudioStreams.add((AudioStream) stream);
		} else {
			mVideoStreams.add((VideoStream) stream);
		}
		return this;
	}

	public FileAttributes setDuration(Double duration) {
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
		mAudioStreams = new ArrayList<>();
		in.readList(mAudioStreams, AudioStream.class.getClassLoader());
		mVideoStreams = new ArrayList<>();
		in.readList(mVideoStreams, VideoStream.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}
