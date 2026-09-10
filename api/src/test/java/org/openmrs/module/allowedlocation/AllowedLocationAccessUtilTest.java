package org.openmrs.module.allowedlocation;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.util.RoleConstants;

/**
 * Tests the rules of {@link AllowedLocationAccessUtil}. They are exercised through the package
 * private resolve method so that the rules can be checked without a database, the reading of the
 * locations and global properties they are applied to is covered by AllowedLocationFilterTest.
 */
public class AllowedLocationAccessUtilTest {

	/** Location 1, parent of 2 and 3, grandparent of 4 */
	private static final String DISTRICT_UUID = "11111111-1111-1111-1111-111111111111";

	/** Location 2, child of 1, parent of 4 */
	private static final String CLINIC_A_UUID = "22222222-2222-2222-2222-222222222222";

	/** Location 3, child of 1, no children */
	private static final String CLINIC_B_UUID = "33333333-3333-3333-3333-333333333333";

	/** Location 5, unrelated to the rest */
	private static final String OTHER_HOSPITAL_UUID = "55555555-5555-5555-5555-555555555555";

	private static final List<List<Object>> LOCATION_ROWS = asList(
	    row(1, DISTRICT_UUID, "District", null),
	    row(2, CLINIC_A_UUID, "Clinic A", 1),
	    row(3, CLINIC_B_UUID, "Clinic B", 1),
	    row(4, "44444444-4444-4444-4444-444444444444", "Ward", 2),
	    row(5, OTHER_HOSPITAL_UUID, "Other Hospital", null));

	private User user;

	private Map<String, String> settings;

	@Before
	public void before() {
		user = new User();
		user.setUserId(1);
		settings = new HashMap<>();
	}

	@Test
	public void resolve_shouldAllowNothingIfThereIsNoAuthenticatedUser() {
		AllowedLocationAccess access = AllowedLocationAccessUtil.resolve(null, settings, LOCATION_ROWS);

		assertTrue(access.isRestricted());
		assertTrue(access.getLocationIds().isEmpty());
	}

	@Test
	public void resolve_shouldNotRestrictASuperUser() {
		user.addRole(new Role(RoleConstants.SUPERUSER));
		setAllowedLocations(CLINIC_A_UUID);

		assertFalse(AllowedLocationAccessUtil.resolve(user, settings, LOCATION_ROWS).isRestricted());
	}

	@Test
	public void resolve_shouldNotRestrictAUserWithNoAllowedLocationsByDefault() {
		assertFalse(AllowedLocationAccessUtil.resolve(user, settings, LOCATION_ROWS).isRestricted());
	}

	@Test
	public void resolve_shouldAllowNothingIfTheUserHasNoAllowedLocationsAndUnsetIsRestricted() {
		settings.put(AllowedLocationConstants.GP_UNRESTRICTED_WHEN_UNSET, "false");

		AllowedLocationAccess access = AllowedLocationAccessUtil.resolve(user, settings, LOCATION_ROWS);

		assertTrue(access.isRestricted());
		assertTrue(access.getLocationIds().isEmpty());
	}

	@Test
	public void resolve_shouldAllowOnlyTheLocationsListedInTheUserProperty() {
		setAllowedLocations(CLINIC_A_UUID + "," + OTHER_HOSPITAL_UUID);
		settings.put(AllowedLocationConstants.GP_INCLUDE_DESCENDANTS, "false");

		assertEquals(idsOf(2, 5), resolvedIds());
	}

	@Test
	public void resolve_shouldIncludeTheDescendantsOfTheAllowedLocations() {
		setAllowedLocations(DISTRICT_UUID);

		assertEquals(idsOf(1, 2, 3, 4), resolvedIds());
	}

	@Test
	public void resolve_shouldResolveLocationNamesInAdditionToUuids() {
		setAllowedLocations("Clinic B");

		assertEquals(idsOf(3), resolvedIds());
	}

	@Test
	public void resolve_shouldIgnoreBlankEntries() {
		setAllowedLocations(" , " + CLINIC_B_UUID + ", ");

		assertEquals(idsOf(3), resolvedIds());
	}

	@Test
	public void resolve_shouldSkipUnknownEntriesAndStillMatchTheKnownOnes() {
		setAllowedLocations("not-a-location," + CLINIC_B_UUID);

		assertEquals(idsOf(3), resolvedIds());
	}

	@Test
	public void resolve_shouldAllowNothingIfNoEntryMatchesALocation() {
		setAllowedLocations("not-a-location");

		AllowedLocationAccess access = AllowedLocationAccessUtil.resolve(user, settings, LOCATION_ROWS);

		assertTrue(access.isRestricted());
		assertTrue(access.getLocationIds().isEmpty());
	}

	@Test
	public void resolve_shouldReadThePropertyNameFromAGlobalProperty() {
		settings.put(AllowedLocationConstants.GP_USER_PROPERTY, "myAllowedLocations");
		user.setUserProperty("myAllowedLocations", CLINIC_B_UUID);
		//The default property is set too, to prove the configured one is the one that gets read
		user.setUserProperty(AllowedLocationConstants.DEFAULT_USER_PROPERTY, OTHER_HOSPITAL_UUID);

		assertEquals(idsOf(3), resolvedIds());
	}

	@Test
	public void asFilterParameter_shouldReturnTheAllowedLocationIds() {
		setAllowedLocations(CLINIC_B_UUID);

		AllowedLocationAccess access = AllowedLocationAccessUtil.resolve(user, settings, LOCATION_ROWS);

		assertEquals(idsOf(3), AllowedLocationAccessUtil.asFilterParameter(access));
	}

	@Test
	public void asFilterParameter_shouldReturnAnUnmatchableIdWhenNothingIsAllowed() {
		Set<Integer> parameter = AllowedLocationAccessUtil
		        .asFilterParameter(AllowedLocationAccess.restrictedToNothing());

		assertEquals(Collections.singleton(AllowedLocationConstants.NO_MATCH_LOCATION_ID), parameter);
	}

	private void setAllowedLocations(String propertyValue) {
		user.setUserProperty(AllowedLocationConstants.DEFAULT_USER_PROPERTY, propertyValue);
	}

	private Set<Integer> resolvedIds() {
		AllowedLocationAccess access = AllowedLocationAccessUtil.resolve(user, settings, LOCATION_ROWS);
		assertTrue(access.isRestricted());

		return access.getLocationIds();
	}

	private static Set<Integer> idsOf(Integer... ids) {
		return new HashSet<>(asList(ids));
	}

	private static List<Object> row(Integer locationId, String uuid, String name, Integer parentLocation) {
		return Arrays.<Object> asList(locationId, uuid, name, parentLocation);
	}

}
