/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.dialplan;

import org.sipfoundry.sipxconfig.admin.InitTaskListener;
import org.springframework.beans.factory.annotation.Required;

public class CleanupDialPlanTask extends InitTaskListener {

    private DialPlanContext m_dialPlanContext;

    @Override
    public void onInitTask(String task) {
        m_dialPlanContext.removeEmptyRules();
    }

    @Required
    public void setDialPlanContext(DialPlanContext dialPlanContext) {
        m_dialPlanContext = dialPlanContext;
    }
}
