package org.openl.rules.webstudio.service;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;

import org.openl.rules.security.Group;

/**
 * External Groups service
 *
 * @author Vladyslav Pikus
 */
public interface ExternalGroupService {

    /**
     * Delete all external groups
     */
    void deleteAll();

    /**
     * Fully replace old user external groups with new ones. Orphan groups will be deleted.
     *
     * @param loginName      username
     * @param externalGroups collections of new external groups.
     */
    void mergeAllForUser(String loginName, Collection<? extends GrantedAuthority> externalGroups);

    /**
     * Find all external groups for user
     *
     * @param loginName username
     * @return found collection of external group for user
     */
    List<Group> findAllForUser(String loginName);

    /**
     * Count all external groups for user
     *
     * @param loginName username
     * @return positive number of external groups otherwise {@code 0}
     */
    long countAllForUser(String loginName);

    /**
     * Find all matched external groups for user. By matched means that the same internal group exists
     *
     * @param loginName username
     * @return found collection of external group for user
     */
    List<Group> findMatchedForUser(String loginName);

    /**
     * Find all matched external groups for user. By matched means that the same internal groups exist
     *
     * @param loginName username
     * @return positive number of matched external groups otherwise {@code 0}
     */
    long countMatchedForUser(String loginName);

    /**
     * Find all not matched external groups for user. By not matched means that the same internal group doesn't exit
     *
     * @param loginName username
     * @return found collection of external group for user
     */
    List<Group> findNotMatchedForUser(String loginName);

    /**
     * Find all not matched external groups for user. By not matched means that the same internal group doesn't exit
     *
     * @param loginName username
     * @return positive number of not matched external groups otherwise {@code 0}
     */
    long countNotMatchedForUser(String loginName);

    /**
     * Search external groups by full group name or fragment
     *
     * @param groupName full group name or term fragment
     * @param limit     max results number
     * @return collection of found external groups
     */
    List<Group> findAllByName(String groupName, int limit);

    /**
     * Count external groups by full group name or fragment
     *
     * @param groupName full group name or term fragment
     * @return positive number of found external groups otherwise {@code 0}
     */
    long countUsersInGroup(String groupName);
}
