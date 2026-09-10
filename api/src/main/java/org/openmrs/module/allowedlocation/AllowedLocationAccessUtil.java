package org.openmrs.module.allowedlocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.AdministrationDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the locations the authenticated user is allowed from their
 * {@value AllowedLocationConstants#DEFAULT_USER_PROPERTY} user property. <pre>
 * This is the module's reusable entry point: any other module that wants to scope its own data to the
 * same locations, rather than reimplementing the user property parsing and its global properties, is
 * expected to call {@link #getAccessibleLocations()} from its own datafilter DataFilterListener. The
 * billing module's billing_locationBasedBillingFilter does exactly that, see the "Reusing this from
 * another module" section of the README.
 * </pre>
 */
public final class AllowedLocationAccessUtil {

	private static final Logger log = LoggerFactory.getLogger(AllowedLocationAccessUtil.class);

	private AllowedLocationAccessUtil() {
	}

	/**
	 * Resolves the locations the authenticated user is allowed. The rules, in order, are:
	 * <ul>
	 * <li>no authenticated user is allowed nothing, which matches how datafilter's own location based
	 * filters treat one</li>
	 * <li>a super user is never restricted</li>
	 * <li>a user with no value for the user property is unrestricted, unless
	 * {@value AllowedLocationConstants#GP_UNRESTRICTED_WHEN_UNSET} has been set to false</li>
	 * <li>a user whose property is set is restricted to the locations it resolves to, plus their
	 * descendants unless {@value AllowedLocationConstants#GP_INCLUDE_DESCENDANTS} is false</li>
	 * <li>a user whose property is set but resolves to nothing is allowed nothing, i.e. this fails
	 * closed rather than open</li>
	 * </ul>
	 *
	 * @return the resolved {@link AllowedLocationAccess}, never null
	 */
	public static AllowedLocationAccess getAccessibleLocations() {
		User user = Context.isAuthenticated() ? Context.getAuthenticatedUser() : null;

		return resolve(user, getSettings(), getLocationRows());
	}

	/**
	 * Converts the specified access into a value that can be bound to a hibernate filter parameter
	 * matched against a location id column.
	 *
	 * @param access the access to convert
	 * @return the location ids to bind, never empty so that the filter fails closed instead of
	 *         rendering an invalid <code>IN ()</code>
	 * @see AllowedLocationConstants#NO_MATCH_LOCATION_ID
	 */
	public static Set<Integer> asFilterParameter(AllowedLocationAccess access) {
		if (access.getLocationIds().isEmpty()) {
			return Collections.singleton(AllowedLocationConstants.NO_MATCH_LOCATION_ID);
		}

		return access.getLocationIds();
	}

	/**
	 * The rules of {@link #getAccessibleLocations()} applied to data that has already been read, kept
	 * separate from the reading so that the rules can be tested on their own.
	 *
	 * @param user the authenticated user, null if there is none
	 * @param settings the values of this module's global properties, absent ones fall back to their
	 *            documented default
	 * @param locationRows every location as location_id, uuid, name and parent_location
	 * @return the resolved {@link AllowedLocationAccess}, never null
	 */
	static AllowedLocationAccess resolve(User user, Map<String, String> settings, List<List<Object>> locationRows) {
		if (user == null) {
			return AllowedLocationAccess.restrictedToNothing();
		}

		if (user.isSuperUser()) {
			log.trace("Not restricting the locations of a super user");

			return AllowedLocationAccess.unrestricted();
		}

		String propertyName = settings.get(AllowedLocationConstants.GP_USER_PROPERTY);
		if (StringUtils.isBlank(propertyName)) {
			propertyName = AllowedLocationConstants.DEFAULT_USER_PROPERTY;
		}

		String propertyValue = user.getUserProperty(propertyName);
		if (StringUtils.isBlank(propertyValue)) {
			if (getBooleanSetting(settings, AllowedLocationConstants.GP_UNRESTRICTED_WHEN_UNSET, true)) {
				log.debug("User {} has no {} user property, leaving them unrestricted", user.getUserId(), propertyName);

				return AllowedLocationAccess.unrestricted();
			}

			log.debug("User {} has no {} user property, no location will be accessible to them", user.getUserId(),
			    propertyName);

			return AllowedLocationAccess.restrictedToNothing();
		}

		Set<Integer> locationIds = resolveLocationIds(propertyValue, locationRows,
		    getBooleanSetting(settings, AllowedLocationConstants.GP_INCLUDE_DESCENDANTS, true));

		if (locationIds.isEmpty()) {
			//The property was set but nothing in it could be resolved, fail closed rather than open.
			log.warn("None of the entries in the {} user property of user {} matched a location", propertyName,
			    user.getUserId());

			return AllowedLocationAccess.restrictedToNothing();
		}

		log.debug("User {} is allowed location id(s): {}", user.getUserId(), locationIds);

		return AllowedLocationAccess.restrictedTo(locationIds);
	}

	/**
	 * Resolves each entry in the specified comma separated list to a location id, an entry is matched on
	 * uuid first and then on name so that a value copied from the legacy admin UI still works. Entries
	 * that match no location are skipped.
	 *
	 * @param propertyValue the comma separated list of location uuids or names
	 * @param locationRows every location as location_id, uuid, name and parent_location
	 * @param includeDescendants specifies whether child locations should be included
	 * @return a collection of location ids
	 */
	private static Set<Integer> resolveLocationIds(String propertyValue, List<List<Object>> locationRows,
	        boolean includeDescendants) {
		Map<String, Integer> idByUuid = new HashMap<>(locationRows.size());
		Map<String, Integer> idByName = new HashMap<>(locationRows.size());
		Map<Integer, Set<Integer>> childIds = new HashMap<>();
		for (List<Object> row : locationRows) {
			final Integer id = toId(row.get(0));
			if (row.get(1) != null) {
				idByUuid.put(row.get(1).toString(), id);
			}
			if (row.get(2) != null) {
				idByName.put(row.get(2).toString(), id);
			}
			if (row.get(3) != null) {
				childIds.computeIfAbsent(toId(row.get(3)), k -> new HashSet<>()).add(id);
			}
		}

		Set<Integer> locationIds = new HashSet<>();
		for (String entry : propertyValue.split(",")) {
			final String identifier = entry.trim();
			if (identifier.isEmpty()) {
				continue;
			}

			Integer id = idByUuid.get(identifier);
			if (id == null) {
				id = idByName.get(identifier);
			}

			if (id == null) {
				log.warn("Ignoring unknown location: {}", identifier);
				continue;
			}

			locationIds.add(id);
			if (includeDescendants) {
				addDescendants(id, childIds, locationIds);
			}
		}

		return locationIds;
	}

	/**
	 * Adds the ids of all the locations nested below the one with the specified id at any depth.
	 *
	 * @param locationId the id of the location whose descendants to add
	 * @param childIds map of each location id to the ids of its immediate children
	 * @param locationIds the collection to add to
	 */
	private static void addDescendants(Integer locationId, Map<Integer, Set<Integer>> childIds, Set<Integer> locationIds) {
		for (Integer childId : childIds.getOrDefault(locationId, Collections.emptySet())) {
			//Guards against a cycle in case of bad data, we would otherwise recurse forever
			if (locationIds.add(childId)) {
				addDescendants(childId, childIds, locationIds);
			}
		}
	}

	/**
	 * @return this module's global properties, an absent row is simply missing from the map
	 * @see AllowedLocationConstants#SETTINGS_QUERY
	 */
	private static Map<String, String> getSettings() {
		Map<String, String> settings = new HashMap<>();
		for (List<Object> row : executeSQL(AllowedLocationConstants.SETTINGS_QUERY)) {
			if (row.get(0) != null && row.get(1) != null) {
				settings.put(row.get(0).toString(), row.get(1).toString());
			}
		}

		return settings;
	}

	/**
	 * @return every location as location_id, uuid, name and parent_location
	 * @see AllowedLocationConstants#ALL_LOCATIONS_QUERY
	 */
	private static List<List<Object>> getLocationRows() {
		return executeSQL(AllowedLocationConstants.ALL_LOCATIONS_QUERY);
	}

	private static boolean getBooleanSetting(Map<String, String> settings, String gpName, boolean defaultValue) {
		String value = settings.get(gpName);

		return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
	}

	private static List<List<Object>> executeSQL(String query) {
		AdministrationDAO adminDAO = Context.getRegisteredComponent("adminDAO", AdministrationDAO.class);

		return adminDAO.executeSQL(query, true);
	}

	private static Integer toId(Object value) {
		return value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(value.toString());
	}

}
