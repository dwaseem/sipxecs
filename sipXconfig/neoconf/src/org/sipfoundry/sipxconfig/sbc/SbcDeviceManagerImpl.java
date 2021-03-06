/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxconfig.sbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.sipfoundry.sipxconfig.bridge.BridgeSbc;
import org.sipfoundry.sipxconfig.common.SipxHibernateDaoSupport;
import org.sipfoundry.sipxconfig.common.UserException;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.feature.FeatureListener;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.feature.GlobalFeature;
import org.sipfoundry.sipxconfig.feature.LocationFeature;
import org.sipfoundry.sipxconfig.feature.FeatureListener.FeatureEvent;
import org.sipfoundry.sipxconfig.logging.AuditLogContext;
import org.sipfoundry.sipxconfig.logging.AuditLogContext.CONFIG_CHANGE_TYPE;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.orm.hibernate3.HibernateCallback;

public class SbcDeviceManagerImpl extends SipxHibernateDaoSupport<SbcDevice> implements SbcDeviceManager,
        BeanFactoryAware, FeatureListener {
    private static final String SBC_ID = "sbcId";
    private static final String SBC_NAME = "sbcName";
    private static final String AUDIT_LOG_CONFIG_TYPE = "SBC Device";
    private BeanFactory m_beanFactory;
    private SbcDescriptor m_sipXbridgeSbcModel;
    private AuditLogContext m_auditLogContext;

    @Required
    public void setAuditLogContext(AuditLogContext auditLogContext) {
        m_auditLogContext = auditLogContext;
    }

    @Required
    public void setSipXbridgeSbcModel(SbcDescriptor sipXbridgeSbcModel) {
        m_sipXbridgeSbcModel = sipXbridgeSbcModel;
    }

    public void clear() {
        Collection<SbcDevice> sbcs = getSbcDevices();
        getDaoEventPublisher().publishDeleteCollection(sbcs);
        for (SbcDevice sbcDevice : sbcs) {
            deleteSbcDevice(sbcDevice.getId());
        }
    }

    public void deleteSbcDevice(Integer id) {
        SbcDevice sbcDevice = getSbcDevice(id);
        getDaoEventPublisher().publishDelete(sbcDevice);
        getHibernateTemplate().delete(sbcDevice);
        m_auditLogContext.logConfigChange(CONFIG_CHANGE_TYPE.DELETED, AUDIT_LOG_CONFIG_TYPE, sbcDevice.getName());
    }

    public void deleteSbcDevice(SbcDevice sbcDevice) {
        getHibernateTemplate().delete(sbcDevice);
        m_auditLogContext.logConfigChange(CONFIG_CHANGE_TYPE.DELETED, AUDIT_LOG_CONFIG_TYPE, sbcDevice.getName());
    }

    public void deleteSbcDevices(Collection<Integer> ids) {
        for (Integer id : ids) {
            SbcDevice sbcDevice = getSbcDevice(id);
            getDaoEventPublisher().publishDelete(sbcDevice);
            deleteSbcDevice(id);
        }
    }

    public Collection<Integer> getAllSbcDeviceIds() {
        return getHibernateTemplate().findByNamedQuery("sbcIds");
    }

    public SbcDevice getSbcDevice(Integer id) {
        return load(SbcDevice.class, id);
    }

    public BridgeSbc getBridgeSbc(Location location) {
        List<BridgeSbc> sbcDevices = getSbcDeviceByType(BridgeSbc.class);
        for (Iterator<BridgeSbc> iterator = sbcDevices.iterator(); iterator.hasNext();) {
            BridgeSbc sbcDevice = iterator.next();
            if (null != location && (location.equals(sbcDevice.getLocation()))) {
                return sbcDevice;
            }
        }
        return null;
    }

    public List<BridgeSbc> getBridgeSbcs() {
        return getSbcDeviceByType(BridgeSbc.class);
    }

    private <T> List<T> getSbcDeviceByType(final Class<T> type) {
        HibernateCallback callback = new HibernateCallback() {
            public Object doInHibernate(Session session) {
                Criteria criteria = session.createCriteria(type);
                return criteria.list();
            }
        };
        return getHibernateTemplate().executeFind(callback);
    }

    private List<SbcDevice> getSbcDevicesByDescriptor(SbcDescriptor descriptor) {
        List<SbcDevice> sbcs = getSbcDevices();
        List<SbcDevice> list = new ArrayList<SbcDevice>();
        for (SbcDevice sbc : sbcs) {
            if (sbc.getModelId().equals(descriptor.getModelId())) {
                list.add(sbc);
            }
        }
        return list;
    }

    public List<SbcDevice> getSbcDevices() {
        return getHibernateTemplate().loadAll(SbcDevice.class);
    }

    public void checkForNewSbcDeviceCreation(SbcDescriptor descriptor) {
        int maxAllowed = descriptor.getMaxAllowed();
        if (descriptor.getMaxAllowed() > -1) {
            int size = getSbcDevicesByDescriptor(descriptor).size();
            if (size >= maxAllowed) {
                throw new UserException("sbc.creation.error", new Object[] {
                    size, descriptor.getLabel()
                });
            }
        }
    }

    public boolean maxAllowedLimitReached(SbcDescriptor model) {
        String type = model.getBeanId();
        int limit = model.getMaxAllowed();
        List count = getHibernateTemplate().findByNamedQueryAndNamedParam("countSbcsByType", "sbcBeanId", type);
        int sbcNumber = DataAccessUtils.intResult(count);
        return limit != -1 && sbcNumber >= limit;
    }

    public BridgeSbc newBridgeSbc(Location location) {
        BridgeSbc bridgeSbc = (BridgeSbc) newSbcDevice(m_sipXbridgeSbcModel);
        bridgeSbc.setName("sipXbridge-" + location.getId().toString());
        bridgeSbc.setDescription("Internal SBC on " + location.getFqdn());
        bridgeSbc.setLocation(location);
        bridgeSbc.setAddress(location.getAddress());
        // Set location id in order to ensure location id saving in DB.
        // Please note that sbc_device table is not related with location table
        bridgeSbc.setSettingTypedValue("bridge-configuration/location-id", location.getId());
        saveSbcDevice(bridgeSbc);
        getDaoEventPublisher().publishSave(bridgeSbc);
        getHibernateTemplate().flush();
        return bridgeSbc;
    }


    public SbcDevice newSbcDevice(SbcDescriptor descriptor) {
        String beanId = descriptor.getBeanId();
        SbcDevice newSbc = (SbcDevice) m_beanFactory.getBean(beanId, SbcDevice.class);
        newSbc.setModel(descriptor);
        newSbc.setPort(descriptor.getDefaultPort());
        return newSbc;
    }

    public void saveSbcDevice(SbcDevice sbc) {
        boolean isNew = sbc.isNew();
        if (isNew) {
            checkForNewSbcDeviceCreation(sbc.getModel());
            checkForDuplicateNames(sbc);
        } else {
            // if the sbc name was changed
            if (isNameChanged(sbc)) {
                checkForDuplicateNames(sbc);
            }
        }
        saveBeanWithSettings(sbc);

        // Replicate occurs only when updating sbc device
        if (isNew) {
            m_auditLogContext.logConfigChange(CONFIG_CHANGE_TYPE.ADDED, AUDIT_LOG_CONFIG_TYPE, sbc.getName());
        } else {
            m_auditLogContext.logConfigChange(CONFIG_CHANGE_TYPE.MODIFIED, AUDIT_LOG_CONFIG_TYPE, sbc.getName());
            sbc.generateProfiles(sbc.getProfileLocation());
            sbc.restart();
        }
    }

    public boolean isInternalSbcEnabled() {
        return (getBridgeSbcs().size() > 0);
    }

    private void checkForDuplicateNames(SbcDevice sbc) {
        if (isNameInUse(sbc)) {
            throw new UserException("error.duplicateSbcName");
        }
    }

    private boolean isNameInUse(SbcDevice sbc) {
        List count = getHibernateTemplate().findByNamedQueryAndNamedParam("anotherSbcWithSameName", new String[] {
            SBC_NAME
        }, new Object[] {
            sbc.getName()
        });

        return DataAccessUtils.intResult(count) > 0;
    }

    private boolean isNameChanged(SbcDevice sbc) {
        List count = getHibernateTemplate().findByNamedQueryAndNamedParam("countSbcWithSameName", new String[] {
            SBC_ID, SBC_NAME
        }, new Object[] {
            sbc.getId(), sbc.getName()
        });

        return DataAccessUtils.intResult(count) == 0;
    }

    public void setBeanFactory(BeanFactory beanFactory) {
        m_beanFactory = beanFactory;
    }

    public List<Sbc> getSbcsForSbcDeviceId(Integer sbcDeviceId) {
        return getHibernateTemplate().findByNamedQueryAndNamedParam("sbcsForSbcDeviceId", SBC_ID, sbcDeviceId);
    }

    @Override
    public void enableLocationFeature(FeatureManager manager, FeatureEvent event, LocationFeature feature,
            Location location) {
        if (feature.equals(SbcManager.FEATURE)) {
            BridgeSbc bridgeSbc = getBridgeSbc(location);
            if (event == FeatureEvent.PRE_ENABLE && bridgeSbc == null) {
                newBridgeSbc(location);
            }
            if (event == FeatureEvent.POST_DISABLE && bridgeSbc != null) {
                deleteSbcDevice(bridgeSbc.getId());
            }
        }
    }

    @Override
    public void enableGlobalFeature(FeatureManager manager, FeatureEvent event, GlobalFeature feature) {
    }
}
