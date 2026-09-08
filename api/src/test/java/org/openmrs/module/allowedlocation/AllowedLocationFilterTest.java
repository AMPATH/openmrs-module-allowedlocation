package org.openmrs.module.allowedlocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.datafilter.TestConstants;
import org.openmrs.module.datafilter.impl.BaseFilterTest;
import org.openmrs.module.datafilter.impl.ImplConstants;
import org.openmrs.test.TestUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ported from datafilter's own (reverted) in-tree test of this filter, unchanged in behaviour, only
 * the package and the constants it references moved from datafilter's ImplConstants to this module's
 * own AllowedLocationConstants. Reuses datafilter's BaseFilterTest and locations.xml/persons.xml/
 * users.xml fixtures via its published test-jar, see api/pom.xml.
 *
 * Not run as part of this change, run manually before relying on it.
 */
public class AllowedLocationFilterTest extends BaseFilterTest {

	@Autowired
	private LocationService locationService;

	private static final String USERNAME = "dBeckham";

	private static final String PASSWORD = "test";

	private static final String NAME_QUERY = "Kampala";

	/** Location 40000, parent of 40003, 40004 and 40005, grandparent of 40006, 40007 and 40008 */
	private static final String NSAMBYA_UUID = "1d6c993f-c2cc-11de-8d13-0010c6dffd0f";

	/** Location 40001, no children */
	private static final String RUBAGA_UUID = "2d6c993f-c2cc-11de-8d13-0010c6dffd0f";

	/** Location 40002, no children */
	private static final String MULAGO_UUID = "3d6c993f-c2cc-11de-8d13-0010c6dffd0f";

	/** Every location in locations.xml that the name query matches */
	private static final int ALL_MATCHING_LOCATIONS = 8;

	@Before
	public void before() {
		executeDataSet(TestConstants.ROOT_PACKAGE_DIR + "persons.xml");
		executeDataSet(TestConstants.ROOT_PACKAGE_DIR + "users.xml");
		executeDataSet(TestConstants.ROOT_PACKAGE_DIR + "locations.xml");
		//datafilter's own basis based location filter is ANDed with the one under test, switch it off
		//so that these assertions are about the user property alone.
		setGlobalProperty(ImplConstants.GP_LOCATION_FILTER_NAME, "true");
	}

	@Test
	public void getLocations_shouldNotRestrictAUserWithNoAllowedLocationsByDefault() {
		//Installing the module must not change what existing users can see until they get the property
		reloginAs(USERNAME, PASSWORD);
		assertEquals(ALL_MATCHING_LOCATIONS, locationService.getLocations(NAME_QUERY).size());
	}

	@Test
	public void getLocations_shouldReturnNoLocationsIfTheUserHasNoAllowedLocationsAndUnsetIsRestricted() {
		setGlobalProperty(AllowedLocationConstants.GP_UNRESTRICTED_WHEN_UNSET, "false");
		reloginAs(USERNAME, PASSWORD);
		assertEquals(0, locationService.getLocations(NAME_QUERY).size());
	}

	@Test
	public void getLocations_shouldReturnNoLocationsIfThereIsNoAuthenticatedUser() {
		Context.logout();
		Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
		try {
			assertEquals(0, locationService.getLocations(NAME_QUERY).size());
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
		}
	}

	@Test
	public void getLocations_shouldReturnOnlyTheLocationsListedInTheUserProperty() {
		setAllowedLocations(RUBAGA_UUID + "," + MULAGO_UUID);
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(2, locations.size());
		assertTrue(TestUtil.containsId(locations, 40001));
		assertTrue(TestUtil.containsId(locations, 40002));
	}

	@Test
	public void getLocations_shouldIncludeTheDescendantsOfTheAllowedLocations() {
		setAllowedLocations(NSAMBYA_UUID);
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		//40000 and its 6 descendants, less 40005 which the name query does not match
		assertEquals(6, locations.size());
		assertTrue(TestUtil.containsId(locations, 40000));
		assertTrue(TestUtil.containsId(locations, 40003));
		assertTrue(TestUtil.containsId(locations, 40004));
		assertTrue(TestUtil.containsId(locations, 40006));
		assertTrue(TestUtil.containsId(locations, 40007));
		assertTrue(TestUtil.containsId(locations, 40008));
	}

	@Test
	public void getLocations_shouldNotIncludeDescendantsIfTheyAreExcluded() {
		setGlobalProperty(AllowedLocationConstants.GP_INCLUDE_DESCENDANTS, "false");
		setAllowedLocations(NSAMBYA_UUID);
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(1, locations.size());
		assertTrue(TestUtil.containsId(locations, 40000));
	}

	@Test
	public void getLocations_shouldResolveLocationNamesInAdditionToUuids() {
		setAllowedLocations("Kampala Rubaga");
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(1, locations.size());
		assertTrue(TestUtil.containsId(locations, 40001));
	}

	@Test
	public void getLocations_shouldIgnoreBlankEntries() {
		setAllowedLocations(" , " + RUBAGA_UUID + ", ");
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(1, locations.size());
		assertTrue(TestUtil.containsId(locations, 40001));
	}

	@Test
	public void getLocations_shouldSkipUnknownEntriesAndStillMatchTheKnownOnes() {
		setAllowedLocations("not-a-location," + RUBAGA_UUID);
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(1, locations.size());
		assertTrue(TestUtil.containsId(locations, 40001));
	}

	@Test
	public void getLocations_shouldReturnNoLocationsIfNoEntryMatchesALocation() {
		setAllowedLocations("not-a-location");
		assertEquals(0, locationService.getLocations(NAME_QUERY).size());
	}

	@Test
	public void getLocations_shouldReturnAllLocationsIfTheAuthenticatedUserIsASuperUser() {
		assertTrue(Context.getAuthenticatedUser().isSuperUser());
		assertEquals(ALL_MATCHING_LOCATIONS, locationService.getLocations(NAME_QUERY).size());
	}

	@Test
	public void getLocations_shouldReturnAllLocationsIfTheFilterIsDisabled() {
		setGlobalProperty(AllowedLocationConstants.FILTER_NAME + ".disabled", "true");
		reloginAs(USERNAME, PASSWORD);
		assertEquals(ALL_MATCHING_LOCATIONS, locationService.getLocations(NAME_QUERY).size());
	}

	@Test
	public void getLocations_shouldStillRestrictAUserThatHasThePropertyWhenUnsetPropertiesAreUnrestricted() {
		setAllowedLocations(RUBAGA_UUID);
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(1, locations.size());
		assertTrue(TestUtil.containsId(locations, 40001));
	}

	@Test
	public void getLocations_shouldReadThePropertyNameFromAGlobalProperty() {
		final String customProperty = "myAllowedLocations";
		setGlobalProperty(AllowedLocationConstants.GP_USER_PROPERTY, customProperty);
		User user = loginAsRestrictedUser();
		user.setUserProperty(customProperty, RUBAGA_UUID);
		//The default property is set too, to prove the configured one is the one that gets read
		user.setUserProperty(AllowedLocationConstants.DEFAULT_USER_PROPERTY, MULAGO_UUID);
		reapplyFilters();
		Collection<Location> locations = locationService.getLocations(NAME_QUERY);
		assertEquals(1, locations.size());
		assertTrue(TestUtil.containsId(locations, 40001));
	}

	private void setAllowedLocations(String value) {
		loginAsRestrictedUser().setUserProperty(AllowedLocationConstants.DEFAULT_USER_PROPERTY, value);
		reapplyFilters();
	}

	/**
	 * The filter reads the property off the authenticated user, so the tests set it in memory rather
	 * than persisting it. Persisting it would call UserService.saveUser, which fails because
	 * dBeckham in moduleTestData.xml and mmulemba in users.xml share the system id 1-3. Persistence
	 * of the property is not this module's concern anyway.
	 */
	private User loginAsRestrictedUser() {
		reloginAs(USERNAME, PASSWORD);

		return Context.getAuthenticatedUser();
	}

	/**
	 * Filters are enabled once per session, this clears the flag so that they get re-evaluated after
	 * a user property or global property has been changed.
	 */
	private void reapplyFilters() {
		org.openmrs.module.datafilter.DataFilterSessionContext.reset();
	}

	private void setGlobalProperty(String property, String value) {
		Context.addProxyPrivilege(PrivilegeConstants.MANAGE_GLOBAL_PROPERTIES);
		try {
			Context.getAdministrationService().setGlobalProperty(property, value);
			Context.flushSession();
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_GLOBAL_PROPERTIES);
		}
	}

}
