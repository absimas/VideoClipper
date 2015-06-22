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

import android.support.annotation.NonNull;
import com.simas.vc.nav_drawer.NavItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Note that the observers aren't guaranteed to be called in the sequence that they were added.
 */
public final class ObservableList extends ArrayList<NavItem> {

	private final ConcurrentHashMap<String, Observer> mObservers = new ConcurrentHashMap<>();

	/**
	 * Register an observer, replacing any previous one with the same tag.
	 * @return true if a previous observer has been replaced, false otherwise
	 */
	public synchronized boolean registerDataSetObserver(Observer observer, String tag) {
		return mObservers.put(tag, observer) != null;
	}

	/**
	 * Unregister an observer for the given tag.
	 * @return true if an observer was successfully removed.
	 */
	public synchronized boolean unregisterDataSetObserver(String tag) {
		return mObservers.remove(tag) != null;
	}

	/**
	 * Remove all observers.
	 */
	public synchronized void unregisterAllObservers() {
		mObservers.clear();
	}

	/**
	 * Notify all observers.
	 */
	public synchronized void notifyChanged() {
		for (Observer observer : mObservers.values()) {
			observer.onChanged();
		}
	}

	@Override
	public boolean add(NavItem object) {
		if (super.add(object)) {
			notifyChanged();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void add(int index, NavItem object) {
		super.add(index, object);
		notifyChanged();
	}

	@Override
	public boolean addAll(Collection<? extends NavItem> collection) {
		if (super.addAll(collection)) {
			notifyChanged();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends NavItem> collection) {
		if (super.addAll(index, collection)) {
			notifyChanged();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean remove(Object object) {
		if (super.remove(object)) {
			notifyChanged();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public NavItem remove(int index) {
		NavItem removed = super.remove(index);
		notifyChanged();
		return removed;
	}

	@Override
	public boolean removeAll(@NonNull Collection<?> collection) {
		if (super.removeAll(collection)) {
			notifyChanged();
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void removeRange(int fromIndex, int toIndex) {
		super.removeRange(fromIndex, toIndex);
		notifyChanged();
	}

	@Override
	public void clear() {
		int oldSize = size();
		super.clear();

		// If size changed, notify
		if (size() != oldSize) {
			notifyChanged();
		}
	}

	public interface Observer {
		void onChanged();
	}

}
