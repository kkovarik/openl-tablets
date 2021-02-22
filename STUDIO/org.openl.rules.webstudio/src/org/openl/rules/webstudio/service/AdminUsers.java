package org.openl.rules.webstudio.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.openl.rules.security.Group;
import org.openl.rules.security.Privilege;
import org.openl.rules.security.Privileges;
import org.openl.rules.security.SimpleUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Allows to create or assign administrators from the properties file.
 * 
 * There are two cases are supported:<br>
 * 1) When roles are managed externally (in LDAP/AD for example). Then ADMIN privilege is set to groups.<br>
 * 2) When roles are managed in WebStudio. Then a group with ADMIN privilege is set to users.<br>
 *
 * @author Yury Molchan
 */
public class AdminUsers {

    @Autowired
    private UserManagementService userService;

    @Autowired
    private GroupManagementService groupService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${security.administrators}")
    private String[] administrators;

    @Value("#{canCreateExternalUsers || environment.getProperty('user.mode') == 'multi'}")
    private boolean isUsers;

    private static final String ADMIN = Privileges.ADMIN.name();
    private static final String ADMIN_GROUP = "Administrators";

    public void init() {
        for (String user : administrators) {
            if (isUsers) {
                createUserAndAssignGroup(user);
            } else {
                setAdminPrivelegyforGroup(user);
            }
        }
    }

    private void createUserAndAssignGroup(String userToAdd) {
        SimpleUser user = (SimpleUser) userService.loadUserByUsername(userToAdd);
        String adminGroup = assignPrivileges(userToAdd);
        if (user == null) {
            userService.addUser(userToAdd, null, null, passwordEncoder.encode(userToAdd));
            userService.updateAuthorities(userToAdd, Collections.singleton(adminGroup));
        } else if (!user.hasPrivilege(ADMIN)) {
            Set<String> groups = new HashSet<>();
            groups.add(adminGroup);
            user.getAuthorities().stream().filter(g -> g instanceof Group).map(Privilege::getName).forEach(groups::add);
            userService.updateAuthorities(userToAdd, groups);
        }
    }

    private String assignPrivileges(String user) {
        Group administrators = groupService.getGroupByName(ADMIN_GROUP);
        if (administrators != null) {
            if (administrators.hasPrivilege(ADMIN)) {
                return ADMIN_GROUP;
            }
        }
        for (Group group : groupService.getGroups()) {
            if (group.hasPrivilege(ADMIN)) {
                return group.getName();
            }
        }
        if (!groupService.isGroupExist(ADMIN_GROUP)) {
            setAdminPrivelegyforGroup(ADMIN_GROUP);
            return ADMIN_GROUP;
        }
        String group = (user + "_Group");
        setAdminPrivelegyforGroup(group);
        return group;
    }

    private void setAdminPrivelegyforGroup(String user) {
        if (!groupService.isGroupExist(user)) {
            groupService.addGroup(user, null);
        }
        groupService.updateGroup(user, Collections.emptySet(), Collections.singleton(ADMIN));
    }
}