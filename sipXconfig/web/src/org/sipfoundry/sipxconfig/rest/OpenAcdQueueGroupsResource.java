/*
 *
 *  OpenAcdQueueGroupsResource.java - A Restlet to read group data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, D. Chang
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.

 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.sipfoundry.sipxconfig.rest;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_XML;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueueGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeAction;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeCondition;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeStep;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdAgentGroupRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdQueueGroupRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdRecipeActionRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdRecipeConditionRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdRecipeStepRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdSkillRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdQueueGroupsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, NONE;

        public static SortField toSortField(String fieldString) {
            if (fieldString == null) {
                return NONE;
            }

            try {
                return valueOf(fieldString.toUpperCase());
            }
            catch (Exception ex) {
                return NONE;
            }
        }
    }


    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(new Variant(TEXT_XML));
        getVariants().add(new Variant(APPLICATION_JSON));

        // pull parameters from url
        m_form = getRequest().getResourceRef().getQueryAsForm();
    }


    // Allowed REST operations
    // -----------------------

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public boolean allowPut() {
        return true;
    }

    @Override
    public boolean allowDelete() {
        return true;
    }

    // GET - Retrieve Groups and single Group
    // --------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        OpenAcdQueueGroupRestInfoFull queueGroupRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                queueGroupRestInfo = createQueueGroupRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_READ_FAILED, "Read Queue Group failed", exception.getLocalizedMessage());
            }

            return new OpenAcdQueueGroupRepresentation(variant.getMediaType(), queueGroupRestInfo);
        }


        // if not single, process request for all
        List<OpenAcdQueueGroup> queueGroups = m_openAcdContext.getQueueGroups();
        List<OpenAcdQueueGroupRestInfoFull> queueGroupsRestInfo = new ArrayList<OpenAcdQueueGroupRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortQueueGroups(queueGroups);

        // set requested based on pagination and get resulting metadata
        metadataRestInfo = addQueueGroups(queueGroupsRestInfo, queueGroups);

        // create final restinfo
        OpenAcdQueueGroupsBundleRestInfo queueGroupsBundleRestInfo = new OpenAcdQueueGroupsBundleRestInfo(queueGroupsRestInfo, metadataRestInfo);

        return new OpenAcdQueueGroupsRepresentation(variant.getMediaType(), queueGroupsBundleRestInfo);
    }


    // PUT - Update or Add single Group
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get group from body
        OpenAcdQueueGroupRepresentation representation = new OpenAcdQueueGroupRepresentation(entity);
        OpenAcdQueueGroupRestInfoFull queueGroupRestInfo = representation.getObject();
        OpenAcdQueueGroup queueGroup;

        // validate input for update or create
        ValidationInfo validationInfo = validate(queueGroupRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                queueGroup = m_openAcdContext.getQueueGroupById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing group
            try {
                updateQueueGroup(queueGroup, queueGroupRestInfo);
                m_openAcdContext.saveQueueGroup(queueGroup);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_WRITE_FAILED, "Update Queue Group failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_UPDATED, "Updated Queue", queueGroup.getId());

            return;
        }


        // otherwise add new agent group
        try {
            queueGroup = createQueueGroup(queueGroupRestInfo);
            m_openAcdContext.saveQueueGroup(queueGroup);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Queue Group failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created Queue Group", queueGroup.getId());
    }


    // DELETE - Delete single Group
    // --------------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdQueueGroup queueGroup;

        // get id then delete a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                queueGroup = m_openAcdContext.getQueueGroupById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteQueueGroup(queueGroup);

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_DELETED, "Deleted Queue Group", queueGroup.getId());

            return;
        }

        // no id string
        RestUtilities.setResponse(getResponse(), ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(OpenAcdQueueGroupRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();
        List<String> relation = Arrays.asList("is", "isNot");
        List<String> condition1 = Arrays.asList("available_agents", "eligible_agents", "calls_queued", "queue_position", "hour", "weekday", "client_calls_queued");
        List<String> condition2 = Arrays.asList("ticks", "client", "media_type", "caller_id", "caller_name");
        List<String> equalityRelation = Arrays.asList("is", "greater", "less");
        List<String> mediaValues = Arrays.asList("voice", "email", "voicemail", "chat");

        String name = restInfo.getName();

        // rest mods the hours with 24 to give a new hour, so allow over 23

        for (int i = 0; i < name.length(); i++) {
            if ((!Character.isLetterOrDigit(name.charAt(i)) && !(Character.getType(name.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && name.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: 'Name' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        for (int i = 0; i < restInfo.getSteps().size(); i++) {
            if (restInfo.getSteps().get(i).getAction().getAction().isEmpty()) {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: 'Action' cannot be empty and must only contain numbers and *";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }

            if (restInfo.getSteps().get(i).getAction().getAction().equals("announce") || restInfo.getSteps().get(i).getAction().getAction().equals("set_priority")) {
                if (restInfo.getSteps().get(i).getAction().getActionValue().isEmpty()) {
                    validationInfo.valid = false;
                    validationInfo.message = "Validation Error: 'Action Value' cannot be empty and must only contain numbers and *";
                    validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                }
            }

            for (int j = 0; j < m_openAcdContext.getClients().size(); j++) {
                if (m_openAcdContext.getClients().get(j).getIdentity().equals(restInfo.getSteps().get(i).getAction().getActionValue())) {
                    validationInfo.valid = false;
                    validationInfo.message = "Validation Error: Client Does not Exist";
                    validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                }
            }

            if (restInfo.getSteps().get(i).getAction().getAction().equals("set_priority")) {
                for (int j = 0; j < restInfo.getSteps().get(i).getAction().getActionValue().length(); j++) {
                    char c = restInfo.getSteps().get(i).getAction().getActionValue().charAt(j);
                    if (!Character.isDigit(c) && c != '*') {
                        validationInfo.valid = false;
                        validationInfo.message = "Validation Error: 'Action Value' must only contain numbers and *";
                        validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                    }
                }
            }

            for (int k = 0; k < restInfo.getSteps().get(i).getConditions().size(); k++) {
                if (restInfo.getSteps().get(i).getConditions().get(k).getCondition().isEmpty() || restInfo.getSteps().get(i).getConditions().get(k).getRelation().isEmpty() || restInfo.getSteps().get(i).getConditions().get(k).getValueCondition().isEmpty()) {
                    validationInfo.valid = false;
                    validationInfo.message = "Validation Error: 'Condtion' cannot be empty";
                    validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;

                }

                if (condition2.contains(restInfo.getSteps().get(i).getConditions().get(k).getCondition())) {
                    if (!(relation.contains(restInfo.getSteps().get(i).getConditions().get(k).getRelation()))) {
                        validationInfo.valid = false;
                        validationInfo.message = "Validation Error: 'Relation' must only be 'is' or 'isNot'";
                        validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                    }

                }

                if (condition1.contains(restInfo.getSteps().get(i).getConditions().get(k).getCondition())) {
                    if (!(equalityRelation.contains(restInfo.getSteps().get(i).getConditions().get(k).getRelation()))) {
                        validationInfo.valid = false;
                        validationInfo.message = "Validation Error: 'Relation' must only be 'is', 'greater', or 'less'";
                        validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                    }
                }

                if (restInfo.getSteps().get(i).getConditions().get(k).getCondition().equals("media_type")) {
                    if (!(mediaValues.contains(restInfo.getSteps().get(i).getConditions().get(k).getValueCondition()))) {
                        validationInfo.valid = false;
                        validationInfo.message = "Validation Error: 'Value Condition' can only be 'voice', 'email', 'voicemail', or 'chat'";
                        validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                    }
                }

                if (restInfo.getSteps().get(i).getConditions().get(k).getCondition().equals("weekday")) {
                    if (Integer.parseInt(restInfo.getSteps().get(i).getConditions().get(k).getValueCondition()) < 1 || Integer.parseInt(restInfo.getSteps().get(i).getConditions().get(k).getValueCondition()) > 7) {
                        validationInfo.valid = false;
                        validationInfo.message = "Validation Error: 'Value Condition' must be between 1 and 7";
                        validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                    }
                }

                if (!(restInfo.getSteps().get(i).getConditions().get(k).getCondition().equals("caller_id") || restInfo.getSteps().get(i).getConditions().get(k).getCondition().equals("caller_name") || restInfo.getSteps().get(i).getConditions().get(k).getCondition().equals("media_type") || restInfo.getSteps().get(i).getConditions().get(k).getCondition().equals("client"))) {
                    for (int j = 0; j < restInfo.getSteps().get(i).getConditions().get(k).getValueCondition().length(); j++) {
                        char c = restInfo.getSteps().get(i).getConditions().get(k).getValueCondition().charAt(j);
                        if (!Character.isDigit(c) && c != '*') {
                            validationInfo.valid = false;
                            validationInfo.message = "Validation Error: 'Value Condition' must only contain numbers and *";
                            validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
                        }
                    }
                }
            }
        }

        return validationInfo;
    }

    private OpenAcdQueueGroupRestInfoFull createQueueGroupRestInfo(int id) throws ResourceException {
        OpenAcdQueueGroupRestInfoFull queueGroupRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupRestInfo;
        List<OpenAcdRecipeStepRestInfo> recipeStepRestInfo;

        OpenAcdQueueGroup queueGroup = m_openAcdContext.getQueueGroupById(id);
        skillsRestInfo = createSkillsRestInfo(queueGroup.getSkills());
        agentGroupRestInfo = createAgentGroupsRestInfo(queueGroup);
        recipeStepRestInfo = createRecipeStepsRestInfo(queueGroup);
        queueGroupRestInfo = new OpenAcdQueueGroupRestInfoFull(queueGroup, skillsRestInfo, agentGroupRestInfo, recipeStepRestInfo);

        return queueGroupRestInfo;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(Set<OpenAcdSkill> groupSkills) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos for single group
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());

        for (OpenAcdSkill groupSkill : groupSkills) {
            skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
            skillsRestInfo.add(skillRestInfo);
        }

        return skillsRestInfo;
    }

    private List<OpenAcdAgentGroupRestInfo> createAgentGroupsRestInfo(OpenAcdQueueGroup queueGroup) {
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo;
        OpenAcdAgentGroupRestInfo agentGroupRestInfo;

        // create list of agent group restinfos for single group
        Set<OpenAcdAgentGroup> groupAgentGroups = queueGroup.getAgentGroups();
        agentGroupsRestInfo = new ArrayList<OpenAcdAgentGroupRestInfo>(groupAgentGroups.size());

        for (OpenAcdAgentGroup groupAgentGroup : groupAgentGroups) {
            agentGroupRestInfo = new OpenAcdAgentGroupRestInfo(groupAgentGroup);
            agentGroupsRestInfo.add(agentGroupRestInfo);
        }

        return agentGroupsRestInfo;
    }

    private List<OpenAcdRecipeStepRestInfo> createRecipeStepsRestInfo(OpenAcdQueueGroup queueGroup) {
        List<OpenAcdRecipeStepRestInfo> recipeStepsRestInfo;
        OpenAcdRecipeStepRestInfo recipeStepRestInfo;

        Set<OpenAcdRecipeStep> groupRecipeSteps = queueGroup.getSteps();
        recipeStepsRestInfo = new ArrayList<OpenAcdRecipeStepRestInfo>(groupRecipeSteps.size());

        for (OpenAcdRecipeStep groupRecipeStep : groupRecipeSteps) {
            recipeStepRestInfo = new OpenAcdRecipeStepRestInfo(groupRecipeStep, createRecipeActionRestInfo(groupRecipeStep), createRecipeConditionsRestInfo(groupRecipeStep));
            recipeStepsRestInfo.add(recipeStepRestInfo);
        }
        return recipeStepsRestInfo;
    }

    private OpenAcdRecipeActionRestInfo createRecipeActionRestInfo(OpenAcdRecipeStep step) {
        OpenAcdRecipeActionRestInfo recipeActionRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;

        // get skills associated with action
        skillsRestInfo = createSkillsRestInfo(step.getAction().getSkills());
        recipeActionRestInfo = new OpenAcdRecipeActionRestInfo(step.getAction(), skillsRestInfo);

        return recipeActionRestInfo;
    }

    private List<OpenAcdRecipeConditionRestInfo> createRecipeConditionsRestInfo(OpenAcdRecipeStep step) {
        List<OpenAcdRecipeConditionRestInfo> recipeConditionsRestInfo;
        OpenAcdRecipeConditionRestInfo recipeConditionRestInfo;


        List<OpenAcdRecipeCondition> groupRecipeConditions = step.getConditions();
        recipeConditionsRestInfo = new ArrayList<OpenAcdRecipeConditionRestInfo>(groupRecipeConditions.size());

        for (OpenAcdRecipeCondition groupRecipeCondition : groupRecipeConditions) {
            recipeConditionRestInfo = new OpenAcdRecipeConditionRestInfo(groupRecipeCondition);
            recipeConditionsRestInfo.add(recipeConditionRestInfo);
        }

        return recipeConditionsRestInfo;
    }

    private MetadataRestInfo addQueueGroups(List<OpenAcdQueueGroupRestInfoFull> queueGroupsRestInfo, List<OpenAcdQueueGroup> queueGroups) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupRestInfo;
        List<OpenAcdRecipeStepRestInfo> recipeStepRestInfo;
        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, queueGroups.size());

        // create list of group restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdQueueGroup queueGroup = queueGroups.get(index);

            skillsRestInfo = createSkillsRestInfo(queueGroup.getSkills());
            agentGroupRestInfo = createAgentGroupsRestInfo(queueGroup);
            recipeStepRestInfo = createRecipeStepsRestInfo(queueGroup);

            OpenAcdQueueGroupRestInfoFull queueGroupRestInfo = new OpenAcdQueueGroupRestInfoFull(queueGroup, skillsRestInfo, agentGroupRestInfo, recipeStepRestInfo);
            queueGroupsRestInfo.add(queueGroupRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortQueueGroups(List<OpenAcdQueueGroup> queueGroups) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(queueGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup1.getName().compareToIgnoreCase(queueGroup2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queueGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup1.getDescription().compareToIgnoreCase(queueGroup2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(queueGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup2.getName().compareToIgnoreCase(queueGroup1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queueGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup2.getDescription().compareToIgnoreCase(queueGroup1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateQueueGroup(OpenAcdQueueGroup queueGroup, OpenAcdQueueGroupRestInfoFull queueGroupRestInfo) {
        String tempString;

        // do not allow empty name
        tempString = queueGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            queueGroup.setName(tempString);
        }

        queueGroup.setDescription(queueGroupRestInfo.getDescription());

        addLists(queueGroup, queueGroupRestInfo);
    }

    private OpenAcdQueueGroup createQueueGroup(OpenAcdQueueGroupRestInfoFull queueGroupRestInfo) {
        OpenAcdQueueGroup queueGroup = new OpenAcdQueueGroup();

        // copy fields from rest info
        queueGroup.setName(queueGroupRestInfo.getName());
        queueGroup.setDescription(queueGroupRestInfo.getDescription());

        addLists(queueGroup, queueGroupRestInfo);

        return queueGroup;
    }

    private void addLists(OpenAcdQueueGroup queueGroup, OpenAcdQueueGroupRestInfoFull queueGroupRestInfo) {
        // remove all skills
        queueGroup.getSkills().clear();

        // set skills
        OpenAcdSkill skill;
        List<OpenAcdSkillRestInfo> skillsRestInfo = queueGroupRestInfo.getSkills();
        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
            queueGroup.addSkill(skill);
        }

        // remove all agent groups
        queueGroup.getAgentGroups().clear();

        // set agent groups
        OpenAcdAgentGroup agentGroup;
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo = queueGroupRestInfo.getAgentGroups();
        for (OpenAcdAgentGroupRestInfo agentGroupRestInfo : agentGroupsRestInfo) {
            agentGroup = m_openAcdContext.getAgentGroupById(agentGroupRestInfo.getId());
            queueGroup.addAgentGroup(agentGroup);
        }

        // remove all current steps
        queueGroup.getSteps().clear();

        // set steps
        OpenAcdRecipeStep step;
        OpenAcdRecipeCondition condition;
        OpenAcdRecipeAction action;
        OpenAcdRecipeActionRestInfo actionRestInfo;

        List<OpenAcdRecipeStepRestInfo> recipeStepsRestInfo = queueGroupRestInfo.getSteps();
        for (OpenAcdRecipeStepRestInfo recipeStepRestInfo : recipeStepsRestInfo) {
            step = new OpenAcdRecipeStep();
            step.setFrequency(recipeStepRestInfo.getFrequency());


            // add conditions
            step.getConditions().clear();
            for (OpenAcdRecipeConditionRestInfo recipeConditionRestInfo : recipeStepRestInfo.getConditions()) {
                condition = new OpenAcdRecipeCondition();
                condition.setCondition(recipeConditionRestInfo.getCondition());
                condition.setRelation(recipeConditionRestInfo.getRelation());
                condition.setValueCondition(recipeConditionRestInfo.getValueCondition());

                step.addCondition(condition);
            }

            // add action
            action = new OpenAcdRecipeAction();
            actionRestInfo = recipeStepRestInfo.getAction();
            action.setAction(actionRestInfo.getAction());
            action.setActionValue(actionRestInfo.getActionValue());

            // set action skills (separate from skills assigned to queue
            for (OpenAcdSkillRestInfo skillRestInfo : actionRestInfo.getSkills()) {
                skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
                action.addSkill(skill);
            }

            step.setAction(action);

            queueGroup.addStep(step);
        }
    }


    // REST Representations
    // --------------------

    static class OpenAcdQueueGroupsRepresentation extends XStreamRepresentation<OpenAcdQueueGroupsBundleRestInfo> {

        public OpenAcdQueueGroupsRepresentation(MediaType mediaType, OpenAcdQueueGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdQueueGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-queue-group", OpenAcdQueueGroupsBundleRestInfo.class);
            xstream.alias("group", OpenAcdQueueGroupRestInfoFull.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("agentGroup", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("step", OpenAcdRecipeStepRestInfo.class);
            xstream.alias("condition", OpenAcdRecipeConditionRestInfo.class);
            xstream.alias("action", OpenAcdRecipeActionRestInfo.class);
        }
    }

    static class OpenAcdQueueGroupRepresentation extends XStreamRepresentation<OpenAcdQueueGroupRestInfoFull> {

        public OpenAcdQueueGroupRepresentation(MediaType mediaType, OpenAcdQueueGroupRestInfoFull object) {
            super(mediaType, object);
        }

        public OpenAcdQueueGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", OpenAcdQueueGroupRestInfoFull.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("agentGroup", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("step", OpenAcdRecipeStepRestInfo.class);
            xstream.alias("condition", OpenAcdRecipeConditionRestInfo.class);
            xstream.alias("action", OpenAcdRecipeActionRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdQueueGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdQueueGroupRestInfoFull> m_groups;

        public OpenAcdQueueGroupsBundleRestInfo(List<OpenAcdQueueGroupRestInfoFull> queueGroups, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = queueGroups;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdQueueGroupRestInfoFull> getGroups() {
            return m_groups;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
