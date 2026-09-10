package org.openmrs.module.allowedlocation;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The locations the authenticated user is allowed, as resolved by
 * {@link AllowedLocationAccessUtil#getAccessibleLocations()}. <pre>
 * Being unrestricted and being allowed nothing are deliberately separate states, since confusing the
 * two is the difference between a user seeing everything and seeing nothing: a filter that finds
 * {@link #isRestricted()} false is expected to stay disabled, while one restricted to an empty set of
 * locations is expected to match no rows at all.
 * </pre>
 */
public final class AllowedLocationAccess implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final AllowedLocationAccess UNRESTRICTED = new AllowedLocationAccess(false,
	        Collections.<Integer> emptySet());

	private static final AllowedLocationAccess NOTHING = new AllowedLocationAccess(true, Collections.<Integer> emptySet());

	private final boolean restricted;

	private final Set<Integer> locationIds;

	private AllowedLocationAccess(boolean restricted, Set<Integer> locationIds) {
		this.restricted = restricted;
		this.locationIds = Collections.unmodifiableSet(locationIds);
	}

	/**
	 * @return an instance for a user whose access is not scoped to any location at all
	 */
	public static AllowedLocationAccess unrestricted() {
		return UNRESTRICTED;
	}

	/**
	 * @return an instance for a user that is allowed no location, i.e. one that fails closed
	 */
	public static AllowedLocationAccess restrictedToNothing() {
		return NOTHING;
	}

	/**
	 * @param locationIds the ids of the locations the user is allowed
	 * @return an instance restricted to the specified locations
	 */
	public static AllowedLocationAccess restrictedTo(Set<Integer> locationIds) {
		if (locationIds == null || locationIds.isEmpty()) {
			return NOTHING;
		}

		return new AllowedLocationAccess(true, new HashSet<>(locationIds));
	}

	/**
	 * @return true if the user's access is scoped to {@link #getLocationIds()}, false if it is not
	 *         scoped by location at all
	 */
	public boolean isRestricted() {
		return restricted;
	}

	/**
	 * @return the ids of the allowed locations, empty when the user is unrestricted and also when they
	 *         are allowed nothing, see {@link #isRestricted()} to tell those apart
	 */
	public Set<Integer> getLocationIds() {
		return locationIds;
	}

	@Override
	public String toString() {
		return restricted ? "restricted to location id(s) " + locationIds : "unrestricted";
	}

}
