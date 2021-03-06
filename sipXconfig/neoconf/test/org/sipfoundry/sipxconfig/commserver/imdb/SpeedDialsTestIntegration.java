package org.sipfoundry.sipxconfig.commserver.imdb;

import static org.sipfoundry.commons.mongo.MongoConstants.IM_ENABLED;
import static org.sipfoundry.commons.mongo.MongoConstants.PERMISSIONS;
import static org.sipfoundry.commons.mongo.MongoConstants.UID;

import java.util.Arrays;
import java.util.List;

import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.permission.PermissionName;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class SpeedDialsTestIntegration extends ImdbTestCase {
    private SpeedDials m_speeddialDataSet;

    public void testGenerateResourceLists() throws Exception {
        loadDataSetXml("domain/DomainSeed.xml");
        loadDataSet("commserver/imdb/speeddials.db.xml");

        User userA = getCoreContext().loadUserByUserName("user_a");
        User userC = getCoreContext().loadUserByUserName("user_c");
        User userB = getCoreContext().loadUserByUserName("user_name_0");
        User userD = getCoreContext().loadUserByUserName("user_name_1");

        List<String> prmlist = Arrays.asList(PermissionName.SUBSCRIBE_TO_PRESENCE.getName());
        List<String> prmlistNoSubscribe = Arrays.asList(PermissionName.MOBILE.getName());

        DBObject user1 = new BasicDBObject().append(ID, "User9991").append(UID, "user_a").append(IM_ENABLED, false);
        user1.put(PERMISSIONS, prmlist);
        DBObject user2 = new BasicDBObject().append(ID, "User9992").append(UID, "user_c").append(IM_ENABLED, false);
        user2.put(PERMISSIONS, prmlist);
        DBObject user3 = new BasicDBObject().append(ID, "User9993").append(UID, "user_name_0")
                .append(IM_ENABLED, true);
        user3.put(PERMISSIONS, prmlistNoSubscribe);
        DBObject user4 = new BasicDBObject().append(ID, "User9994").append(UID, "user_name_1")
                .append(IM_ENABLED, true);
        user4.put(PERMISSIONS, prmlistNoSubscribe);

        m_speeddialDataSet.generate(userA, user1);
        m_speeddialDataSet.generate(userC, user2);
        m_speeddialDataSet.generate(userB, user3);
        m_speeddialDataSet.generate(userD, user4);
        
        // missing assertions. 
    }

    public void setSpeeddialDataSet(SpeedDials speedDials) {
        m_speeddialDataSet = speedDials;
    }
}
