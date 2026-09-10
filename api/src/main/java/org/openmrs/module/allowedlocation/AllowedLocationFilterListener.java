package org.openmrs.module.allowedlocation;

import org.openmrs.module.datafilter.DataFilterContext;
import org.openmrs.module.datafilter.DataFilterListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sets the parameter for {@link AllowedLocationConstants#FILTER_NAME} from the locations the
 * authenticated user is allowed, as resolved by {@link AllowedLocationAccessUtil}. <pre>
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
		AllowedLocationAccess access = AllowedLocationAccessUtil.getAccessibleLocations();
		if (!access.isRestricted()) {
			log.trace("Leaving the allowed location filter disabled, the user is {}", access);

			return false;
		}

		log.debug("Filtering locations, the user is {}", access);

		filterContext.setParameter(AllowedLocationConstants.PARAM_NAME_ALLOWED_LOCATION_IDS,
		    AllowedLocationAccessUtil.asFilterParameter(access));

		return true;
	}

}
