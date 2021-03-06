/**
 *
 *
 * Copyright (c) 2012 eZuce, Inc. All rights reserved.
 * Contributed to SIPfoundry under a Contributor Agreement
 *
 * This software is free software; you can redistribute it and/or modify it under
 * the terms of the Affero General Public License (AGPL) as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 */
package org.sipfoundry.sipxconfig.mongo;

import org.sipfoundry.sipxconfig.address.AddressType;
import org.sipfoundry.sipxconfig.feature.LocationFeature;

public interface MongoManager {
    public static final String BEAN_ID = "mongo";
    public static final AddressType ADDRESS_ID = new AddressType(BEAN_ID);
    public static final LocationFeature FEATURE_ID = new LocationFeature(BEAN_ID);
    public static final LocationFeature ARBITER_FEATURE = new LocationFeature("mongoArbiter");

    public MongoSettings getSettings();

    public void saveSettings(MongoSettings settings);
}
