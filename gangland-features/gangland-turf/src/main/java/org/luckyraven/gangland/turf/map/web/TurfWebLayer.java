package org.luckyraven.gangland.turf.map.web;

import org.luckyraven.gangland.turf.data.Turf;

/**
 * Type-free handle on the optional web-map turf layer. Nothing in this interface names a dynmap type, so beans and
 * listeners can hold it on a server without dynmap.
 */
public interface TurfWebLayer {

	/**
	 * Redraws every turf and drops markers of turfs that no longer exist.
	 */
	void rebuild();

	/**
	 * Redraws one turf (new turf or changed owner).
	 */
	void sync(Turf turf);

	/**
	 * Removes the marker of a deleted turf.
	 */
	void remove(int turfId);

	/**
	 * Deletes the layer; called on disable and reload.
	 */
	void shutdown();
}
