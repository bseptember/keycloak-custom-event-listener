package com.bseptember.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CustomEventListenerProvider
        implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(CustomEventListenerProvider.class);

    private final KeycloakSession session;
    private final RealmProvider model;

    public CustomEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.model = session.realms();
    }

    @Override
    public void onEvent(Event event) {

        log.debugf("New %s Event", event.getType());
        log.debugf("onEvent-> %s", toString(event));

        if (EventType.REGISTER.equals(event.getType())) {

            event.getDetails().forEach((key, value) -> log.debugf("%s : %s", key, value));

            RealmModel realm = this.model.getRealm(event.getRealmId());

            // Use UserProvider to get the user by ID
            UserProvider userProvider = session.users();
            UserModel user = userProvider.getUserById(realm, event.getUserId());

            // Add user to group if it exists
            addToGroup(user);
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
        log.debug("onEvent(AdminEvent)");
        log.debugf("Resource path: %s", adminEvent.getResourcePath());
        log.debugf("Resource type: %s", adminEvent.getResourceType());
        log.debugf("Operation type: %s", adminEvent.getOperationType());
        log.debugf("AdminEvent.toString(): %s", toString(adminEvent));
        if (ResourceType.USER.equals(adminEvent.getResourceType())
                && OperationType.CREATE.equals(adminEvent.getOperationType())) {
            RealmModel realm = this.model.getRealm(adminEvent.getRealmId());

            // Use UserProvider to get the user by ID
            UserProvider userProvider = session.users();
            UserModel user = userProvider.getUserById(realm, adminEvent.getResourcePath().substring(6));

            // Add user to group if it exists
            addToGroup(user);
        }
    }

    private void addToGroup(UserModel user) {
        try {
            // Extract the domain from the user's email address
            String email = user.getEmail();
            if (email != null) {
                int atIndex = email.indexOf('@');
                if (atIndex != -1) {
                    String domain = email.substring(atIndex + 1);

                    // Check if the group exists
                    RealmModel realm = session.getContext().getRealm();
                    String groupName = domain.toLowerCase(); // Group name based on domain

                    boolean groupExists = false;
                    GroupModel targetGroup = null;

                    // Check if the group already exists
                    Stream<GroupModel> groupStream = realm.getGroupsStream();
                    for (GroupModel group : groupStream.collect(Collectors.toList())) {
                        if (groupName.equalsIgnoreCase(group.getName())) {
                            groupExists = true;
                            targetGroup = group;
                            break;
                        }
                    }

                    //Comment out the following statement if you only want users to be added to manually created groups
                    // If the group doesn't exist, create it
                    if (!groupExists) {
                        targetGroup = realm.createGroup(groupName);
                        targetGroup.setName(groupName);
                    }

                    // If the group exists, add user to it
                    if (groupExists) {
                        user.joinGroup(targetGroup); // Add the user to the group
                        log.debugf("User %s added to group %s", user.getUsername(), groupName);
                    }
                }
            }
        } catch (Exception e) {
            log.errorf("Failed to add user to group: %s", e);
        }
    }

    @Override
    public void close() {}

    private String toString(Event event) {

        StringBuilder sb = new StringBuilder();
        sb.append("type=");
        sb.append(event.getType());
        sb.append(", realmId=");
        sb.append(event.getRealmId());
        sb.append(", clientId=");
        sb.append(event.getClientId());
        sb.append(", userId=");
        sb.append(event.getUserId());
        sb.append(", ipAddress=");
        sb.append(event.getIpAddress());
        if (event.getError() != null) {
            sb.append(", error=");
            sb.append(event.getError());
        }


        if (event.getDetails() != null) {
            for (Map.Entry<String, String> e : event.getDetails().entrySet()) {
                sb.append(", ");
                sb.append(e.getKey());
                if (e.getValue() == null || e.getValue().indexOf(' ') == -1) {
                    sb.append("=");
                    sb.append(e.getValue());
                } else {
                    sb.append("='");
                    sb.append(e.getValue());
                    sb.append("'");
                }
            }
        }

        return sb.toString();
    }

    private String toString(AdminEvent event) {

        RealmModel realm = this.model.getRealm(event.getRealmId());

        UserModel newRegisteredUser =
                this.session.users().getUserById(realm, event.getAuthDetails().getUserId());


        StringBuilder sb = new StringBuilder();
        sb.append("operationType=");
        sb.append(event.getOperationType());
        sb.append(", realmId=");
        sb.append(event.getAuthDetails().getRealmId());
        sb.append(", clientId=");
        sb.append(event.getAuthDetails().getClientId());
        sb.append(", userId=");
        sb.append(event.getAuthDetails().getUserId());

        if (newRegisteredUser != null) {
            sb.append(", email=");
            sb.append(newRegisteredUser.getEmail());
            sb.append(", getUsername=");
            sb.append(newRegisteredUser.getUsername());
            sb.append(", getFirstName=");
            sb.append(newRegisteredUser.getFirstName());
            sb.append(", getLastName=");
            sb.append(newRegisteredUser.getLastName());
        }
        sb.append(", ipAddress=");
        sb.append(event.getAuthDetails().getIpAddress());
        sb.append(", resourcePath=");
        sb.append(event.getResourcePath());
        if (event.getError() != null) {
            sb.append(", error=");
            sb.append(event.getError());
        }

        return sb.toString();
    }
}
