package org.openmrs.module.allowedlocation;

import org.openmrs.module.BaseModuleActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllowedLocationActivator extends BaseModuleActivator {

	private static final Logger log = LoggerFactory.getLogger(AllowedLocationActivator.class);

	/**
	 * @see BaseModuleActivator#started()
	 */
	@Override
	public void started() {
		log.info("Allowed Location Module started");
	}

	/**
	 * @see BaseModuleActivator#stopped()
	 */
	@Override
	public void stopped() {
		log.info("Allowed Location Module stopped");
	}

}
