package org.openmrs.module.allowedlocation;

public final class AllowedLocationConstants {

	public static final String MODULE_ID = "allowedlocation";

	/**
	 * Registered in api/src/main/resources/filters/hibernate/allowed_location.json, picked up by
	 * datafilter's classpath*:/filters/hibernate/*.json scan the same way as any of its own filters.
	 */
	public static final String FILTER_NAME = MODULE_ID + "_allowedLocationFilter";

	public static final String PARAM_NAME_ALLOWED_LOCATION_IDS = "allowedLocationIds";

	public static final String DEFAULT_USER_PROPERTY = "allowed_location";

	/**
	 * Ids are all greater than zero, so a filter parameter set to this matches no rows, it is used to
	 * make a filter fail closed for a user that is allowed nothing.
	 *
	 * @see AllowedLocationAccessUtil#asFilterParameter(AllowedLocationAccess)
	 */
	public static final Integer NO_MATCH_LOCATION_ID = -1;

	public static final String GP_USER_PROPERTY = MODULE_ID + ".userProperty";

	public static final String GP_INCLUDE_DESCENDANTS = MODULE_ID + ".includeDescendants";

	public static final String GP_UNRESTRICTED_WHEN_UNSET = MODULE_ID + ".unrestrictedWhenUnset";

	/**
	 * Read with raw sql by AllowedLocationAccessUtil so that the lookup cannot be hidden by the very
	 * filter it is resolving the parameter for, this filter targets Location itself and is typically
	 * already enabled on the current session with the parameter from a previous evaluation. AccessUtil
	 * in the datafilter module reads its own basis map the same way and for the same reason.
	 */
	public static final String ALL_LOCATIONS_QUERY = "SELECT location_id, uuid, name, parent_location FROM location";

	/**
	 * The global properties above, read with raw sql for the same reason as the locations, and also
	 * because a service call while datafilter is handing out the session re-enters it. Note that
	 * AdministrationDAO.executeSQL runs on the session's jdbc connection rather than through hibernate,
	 * so it neither triggers a flush nor gets filtered. The property names are constants, never input.
	 */
	public static final String SETTINGS_QUERY = "SELECT property, property_value FROM global_property WHERE property IN ('"
	        + GP_USER_PROPERTY + "','" + GP_INCLUDE_DESCENDANTS + "','" + GP_UNRESTRICTED_WHEN_UNSET + "')";

	private AllowedLocationConstants() {
	}

}
