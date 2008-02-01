/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * 
 */
package org.sipfoundry.sipxconfig.admin.dialplan.sbc;

import java.util.Collection;

import org.sipfoundry.sipxconfig.IntegrationTestCase;
import org.sipfoundry.sipxconfig.common.UserException;
import org.sipfoundry.sipxconfig.device.ModelSource;

public class SbcDeviceManagerImplTestIntegration extends IntegrationTestCase {
    private SbcDeviceManager m_sdm;

    private ModelSource<SbcDescriptor> m_modelSource;

    public void testNewSbc() {
        Collection<SbcDescriptor> models = m_modelSource.getModels();
        // at least generic model is empty
        assertFalse(models.isEmpty());
        for (SbcDescriptor model : models) {
            SbcDevice newSbcDevice = m_sdm.newSbcDevice(model);
            assertEquals(newSbcDevice.getBeanId(), model.getBeanId());
            assertEquals(newSbcDevice.getModelId(), model.getModelId());
            assertSame(newSbcDevice.getModel(), model);
        }
    }

    public void testClear() {
        loadDataSet("admin/dialplan/sbc/sbc-device.db.xml");
        assertEquals(3, countRowsInTable("sbc_device"));
        m_sdm.clear();
        flush();

        assertEquals(0, countRowsInTable("sbc_device"));
    }

    public void testGetAllSbcDeviceIds() {
        loadDataSet("admin/dialplan/sbc/sbc-device.db.xml");
        Collection<Integer> allSbcDeviceIds = m_sdm.getAllSbcDeviceIds();
        assertEquals(3, allSbcDeviceIds.size());
        assertTrue(allSbcDeviceIds.contains(1000));
        assertTrue(allSbcDeviceIds.contains(1001));
        assertTrue(allSbcDeviceIds.contains(1002));
    }

    public void testGetSbcDevice() {
        loadDataSet("admin/dialplan/sbc/sbc-device.db.xml");
        SbcDevice sbc = m_sdm.getSbcDevice(1001);

        assertEquals(Integer.valueOf(1001), sbc.getId());
        assertEquals("Sbc1001", sbc.getName());
        assertEquals("10.1.2.4", sbc.getAddress());
        assertEquals("101122334455", sbc.getSerialNumber());
        assertEquals("description1", sbc.getDescription());
    }

    public void testSave() {
        SbcDescriptor model = m_modelSource.getModel("sbcGenericModel");
        SbcDevice newSbcDevice = m_sdm.newSbcDevice(model);
        m_sdm.storeSbcDevice(newSbcDevice);
        assertFalse(newSbcDevice.isNew());
        flush();

        assertEquals(1, countRowsInTable("sbc_device"));

        newSbcDevice.setName("abc");
        m_sdm.storeSbcDevice(newSbcDevice);
    }

    public void testSaveDuplicate() {
        SbcDescriptor model = m_modelSource.getModel("sbcGenericModel");
        SbcDevice newSbcDevice = m_sdm.newSbcDevice(model);
        newSbcDevice.setName("aaa");
        m_sdm.storeSbcDevice(newSbcDevice);
        flush();

        try {
            newSbcDevice = m_sdm.newSbcDevice(model);
            newSbcDevice.setName("aaa");
            m_sdm.storeSbcDevice(newSbcDevice);
            fail("Should not accept duplicated name.");
        } catch (UserException e) {
            // ok - duplicate name
        }
    }

    public void setSbcDeviceManager(SbcDeviceManager sdm) {
        m_sdm = sdm;
    }

    public void setSbcModelSource(ModelSource<SbcDescriptor> modelSource) {
        m_modelSource = modelSource;
    }
}
