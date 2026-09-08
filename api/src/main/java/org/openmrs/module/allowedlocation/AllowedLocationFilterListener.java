package org.openmrs.module.allowedlocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.AdministrationDAO;
import org.openmrs.module.datafilter.DataFilterContext;
import org.openmrs.module.datafilter.DataFilterListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sets the parameter for {@link AllowedLocationConstants#FILTER_NAME} from a user property on the
 * authenticated user, by default one named {@value AllowedLocationConstants#DEFAULT_USER_PROPERTY}
 * holding a comma separated list of location uuids. <pre>
 * This is a datafilter {@link DataFilterListener}, discovered by datafilter across every module's
 * classpath via Context.getRegisteredComponents(DataFilterListener.class), the same way its own
 * built-in listeners are. Only org.openmrs.Location is filtered this way. datafilter's own location
 * based filters for patients and their clinical data keep reading its datafilter_entity_basis_map
 * table, because its AccessInterceptor independently re-checks those types against that table, so
 * driving them from a user property here would let the filter and the interceptor disagree.
 * </pre>
 */
@Component("allowedLocationFilterListener")
public class AllowedLocationFilterListener implements DataFilterListener {

	private static final Logger log = LoggerFactory.getLogger(AllowedLocationFilterListener.class);

	/**
	 * @see DataFilterListener#supports(String)
	 */
	@Override
	public boolean supports(String filterName) {
		return AllowedLocationConstants.FILTER_NAME.equals(filterName);
	}

	/**
	 * @see DataFilterListener#onEnableFilter(DataFilterContext)
	 */
	@Override
	public boolean onEnableFilter(DataFilterContext filterContext) {
		if (!Context.isAuthenticated()) {
			//Matches the behaviour of datafilter's own basis based location filter, no user implies
			//no locations.
			filterContext.setParameter(AllowedLocationConstants.PARAM_NAME_ALLOWED_LOCATION_IDS, noMatch());

			return true;
		}

		User user = Context.getAuthenticatedUser();
		if (user.isSuperUser()) {
			log.trace("Skipping enabling of the allowed location filter for super user");

			return false;
		}

		String propertyName = getGpValue(AllowedLocationConstants.GP_USER_PROPERTY);
		if (StringUtils.isBlank(propertyName)) {
			propertyName = AllowedLocationConstants.DEFAULT_USER_PROPERTY;
		}

		String propertyValue = user.getUserProperty(propertyName);
		if (StringUtils.isBlank(propertyValue)) {
			if (getBooleanGpValue(AllowedLocationConstants.GP_UNRESTRICTED_WHEN_UNSET, true)) {
				log.debug("User {} has no {} user property, leaving the allowed location filter disabled",
				    user.getUserId(), propertyName);

				return false;
			}

			log.debug("User {} has no {} user property, no locations will be accessible to them", user.getUserId(),
			    propertyName);

			filterContext.setParameter(AllowedLocationConstants.PARAM_NAME_ALLOWED_LOCATION_IDS, noMatch());

			return true;
		}

		Set<String> locationIds = resolveLocationIds(propertyValue,
		    getBooleanGpValue(AllowedLocationConstants.GP_INCLUDE_DESCENDANTS, true));

		if (locationIds.isEmpty()) {
			//The property was set but nothing in it could be resolved, fail closed rather than open.
			log.warn("None of the entries in the {} user property of user {} matched a location", propertyName,
			    user.getUserId());

			locationIds = noMatch();
		}

		if (log.isDebugEnabled()) {
			log.debug("Filtering locations for user " + user.getUserId() + " on location id(s): "
			        + String.join(",", locationIds));
		}

		filterContext.setParameter(AllowedLocationConstants.PARAM_NAME_ALLOWED_LOCATION_IDS, locationIds);

		return true;
	}

	/**
	 * Resolves each entry in the specified comma separated list to a location id, an entry is matched
	 * on uuid first and then on name so that a value copied from the legacy admin UI still works.
	 * Entries that match no location are skipped. <pre>
	 * The lookup deliberately goes through raw sql rather than the LocationService or LocationDAO,
	 * because this filter targets Location itself and is typically already enabled on the current
	 * session with the parameter from a previous evaluation, which would hide the very rows we are
	 * trying to resolve.
	 * </pre>
	 *
	 * @param propertyValue the comma separated list of location uuids or names
	 * @param includeDescendants specifies whether child locations should be included
	 * @return a collection of location ids
	 */
	private Set<String> resolveLocationIds(String propertyValue, boolean includeDescendants) {
		AdministrationDAO adminDAO = Context.getRegisteredComponent("adminDAO", AdministrationDAO.class);
		List<List<Object>> rows = adminDAO.executeSQL(AllowedLocationConstants.ALL_LOCATIONS_QUERY, true);

		Map<String, String> idByUuid = new HashMap<>(rows.size());
		Map<String, String> idByName = new HashMap<>(rows.size());
		Map<String, Set<String>> childIds = new HashMap<>();
		for (List<Object> row : rows) {
			final String id = row.get(0).toString();
			if (row.get(1) != null) {
				idByUuid.put(row.get(1).toString(), id);
			}
			if (row.get(2) != null) {
				idByName.put(row.get(2).toString(), id);
			}
			if (row.get(3) != null) {
				childIds.computeIfAbsent(row.get(3).toString(), k -> new HashSet<>()).add(id);
			}
		}

		Set<String> locationIds = new HashSet<>();
		for (String entry : propertyValue.split(",")) {
			final String identifier = entry.trim();
			if (identifier.isEmpty()) {
				continue;
			}

			String id = idByUuid.get(identifier);
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
	private void addDescendants(String locationId, Map<String, Set<String>> childIds, Set<String> locationIds) {
		for (String childId : childIds.getOrDefault(locationId, Collections.emptySet())) {
			//Guards against a cycle in case of bad data, we would otherwise recurse forever
			if (locationIds.add(childId)) {
				addDescendants(childId, childIds, locationIds);
			}
		}
	}

	private Set<String> noMatch() {
		return Collections.singleton(AllowedLocationConstants.NO_MATCH_LOCATION_ID);
	}

	/**
	 * Gets a GP value without triggering a hibernate flush, we are called from datafilter's
	 * DataFilterSessionContext while a session is being handed out and a flush at that point would
	 * re-enter it.
	 *
	 * @param gpName the name of the global property
	 * @return the global property value
	 */
	private String getGpValue(String gpName) {
		Session session = Context.getRegisteredComponents(SessionFactory.class).get(0).getCurrentSession();
		final FlushMode flushMode = session.getHibernateFlushMode();
		session.setHibernateFlushMode(FlushMode.MANUAL);
		try {
			return Context.getAdministrationService().getGlobalProperty(gpName);
		}
		finally {
			session.setHibernateFlushMode(flushMode);
		}
	}

	private boolean getBooleanGpValue(String gpName, boolean defaultValue) {
		String value = getGpValue(gpName);

		return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
	}

}
