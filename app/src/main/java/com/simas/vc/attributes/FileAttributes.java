package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

public class FileAttributes extends Attributes {

	private String mFileName, mLongName, mName;
	private Long mSize = 0l;
	private List<Attributes> mStreams = new ArrayList<>();

	public FileAttributes() {
		setType(Type.FILE);
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

	public List<Attributes> getStreams() {
		return mStreams;
	}

	public void setName(String name) {
		this.mName = name;
	}

	public void setLongName(String longName) {
		this.mLongName = longName;
	}

	public void setFileName(String fileName) {
		this.mFileName = fileName;
	}

	public void setSize(Long size) {
		this.mSize = size;
	}

	public void addStream(Attributes attributes) {
		mStreams.add(attributes);
	}

	@Override
	public String toString() {
		return String.format("%s\nFileAttributes FileName: %s, LongName: %s, Name: %s",
				super.toString(), getFileName(), getLongName(), getName());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		out.writeValue(mFileName);
		out.writeValue(mLongName);
		out.writeValue(mName);
		out.writeValue(mSize);
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
		super(in);
		mFileName = (String) in.readValue(String.class.getClassLoader());
		mLongName = (String) in.readValue(String.class.getClassLoader());
		mName = (String) in.readValue(String.class.getClassLoader());
		mSize = (Long) in.readValue(Long.class.getClassLoader());
		mStreams = new ArrayList<>();
		in.readList(mStreams, Attributes.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}
