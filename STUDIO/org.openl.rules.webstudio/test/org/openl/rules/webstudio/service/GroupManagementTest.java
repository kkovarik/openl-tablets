package org.openl.rules.webstudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.openl.rules.security.Group;
import org.openl.rules.security.Privilege;
import org.openl.rules.security.SimplePrivilege;
import org.openl.rules.webstudio.service.config.UserManagementConfiguration;

@SpringJUnitConfig(classes = {DBTestConfiguration.class,
        UserManagementConfiguration.class,
        AclServiceTestConfiguration.class})
@TestPropertySource(properties = {"db.url = jdbc:h2:mem:temp;DB_CLOSE_DELAY=-1",
        "db.user =",
        "db.password =",
        "db.maximumPoolSize = 3"})
public class GroupManagementTest {

    private static final String RND_GROUP = "GROUP_%s";

    @Autowired
    private ExternalGroupService externalGroupService;
    @Autowired
    private UserManagementService userService;
    @Autowired
    private GroupManagementService groupService;

    @Autowired
    @Qualifier("flywayDBReset")
    private Flyway flywayDBReset;
    @Autowired
    private UserManagementService userManagementService;

    @BeforeEach
    public void setUp() {
        // Reset all changes where done while testing
        flywayDBReset.clean();
        flywayDBReset.migrate();
        QueryCountHolder.clear();
    }

    @Test
    public void testInitialization() {
        initOneUser();
        Set<Privilege> privileges = generatePrivilege(51, "Analysts", "Deployers");
        externalGroupService.mergeAllForUser("jdoe", privileges);
        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(3, queryCount.getInsert());
        assertEquals(2, queryCount.getDelete());
        assertEquals(6, queryCount.getTotal());

        QueryCountHolder.clear();
        List<Group> extGroups = externalGroupService.findAllForUser("jdoe");
        assertCollectionEquals(privileges.stream().map(Privilege::getName).collect(Collectors.toList()),
                extGroups,
                Group::getName);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        long cntExternalGroups = externalGroupService.countAllForUser("jdoe");
        assertEquals(privileges.size(), cntExternalGroups);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        List<Group> matchedExtGroups = externalGroupService.findMatchedForUser("jdoe");
        assertCollectionEquals(Stream.of("Analysts", "Deployers").collect(Collectors.toSet()),
                matchedExtGroups,
                Group::getName);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(3, queryCount.getSelect());
        assertEquals(3, queryCount.getTotal());

        QueryCountHolder.clear();
        long cntMatchedExternalGroups = externalGroupService.countMatchedForUser("jdoe");
        assertEquals(2, cntMatchedExternalGroups);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        List<Group> notMatchedExtGroups = externalGroupService.findNotMatchedForUser("jdoe");
        assertCollectionEquals(privileges.stream()
                .map(Privilege::getName)
                .filter(p -> !"Analysts".equals(p) && !"Deployers".equals(p))
                .collect(Collectors.toList()), notMatchedExtGroups, Group::getName);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        long cntNotMatchedExternalGroups = externalGroupService.countNotMatchedForUser("jdoe");
        assertEquals(51, cntNotMatchedExternalGroups);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        userService.deleteUser("jdoe");
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getDelete());
        assertEquals(2, queryCount.getTotal());

        QueryCountHolder.clear();
        extGroups = externalGroupService.findAllForUser("jdoe");
        assertTrue(extGroups.isEmpty());

        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());
    }

    @Test
    public void testDeleteAll() {
        initOneUser();

        Set<Privilege> privileges = generatePrivilege(10, "Analysts", "Deployers");
        externalGroupService.mergeAllForUser("jdoe", privileges);
        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getInsert());
        assertEquals(2, queryCount.getDelete());
        assertEquals(5, queryCount.getTotal());

        QueryCountHolder.clear();
        List<Group> extGroups = externalGroupService.findAllForUser("jdoe");
        assertCollectionEquals(privileges.stream().map(Privilege::getName).collect(Collectors.toList()),
                extGroups,
                Group::getName);
        long cntExternalGroups = externalGroupService.countAllForUser("jdoe");
        assertEquals(privileges.size(), cntExternalGroups);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getSelect());
        assertEquals(2, queryCount.getTotal());

        QueryCountHolder.clear();
        externalGroupService.deleteAll();
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getDelete());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        testEmpty();
    }

    @Test
    public void testDoubleMerge() {
        initOneUser();

        Set<Privilege> privileges = generatePrivilege(10, "Analysts", "Deployers");
        externalGroupService.mergeAllForUser("jdoe", privileges);
        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getInsert());
        assertEquals(2, queryCount.getDelete());
        assertEquals(5, queryCount.getTotal());

        QueryCountHolder.clear();
        List<Group> extGroups = externalGroupService.findAllForUser("jdoe");
        assertCollectionEquals(privileges.stream().map(Privilege::getName).collect(Collectors.toList()),
                extGroups,
                Group::getName);
        long cntExternalGroups = externalGroupService.countAllForUser("jdoe");
        assertEquals(12, cntExternalGroups);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getSelect());
        assertEquals(2, queryCount.getTotal());

        // New groups must override the old one
        QueryCountHolder.clear();
        privileges = generatePrivilege(9);
        externalGroupService.mergeAllForUser("jdoe", privileges);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getInsert());
        assertEquals(2, queryCount.getDelete());
        assertEquals(5, queryCount.getTotal());

        QueryCountHolder.clear();
        extGroups = externalGroupService.findAllForUser("jdoe");
        assertCollectionEquals(privileges.stream().map(Privilege::getName).collect(Collectors.toList()),
                extGroups,
                Group::getName);
        cntExternalGroups = externalGroupService.countAllForUser("jdoe");
        assertEquals(9, cntExternalGroups);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getSelect());
        assertEquals(2, queryCount.getTotal());
    }

    @Test
    public void testMerge_async() throws InterruptedException {
        initOneUser();
        Set<Privilege> privileges = generatePrivilege(10);
        AtomicInteger successCnt = new AtomicInteger(0);

        final int workersCnt = 10;
        var threadExecutor = Executors.newFixedThreadPool(workersCnt);
        for (int i = 0; i < workersCnt; i++) {
            threadExecutor.execute(() -> {
                try {
                    externalGroupService.mergeAllForUser("jdoe", privileges);
                    successCnt.incrementAndGet();
                } catch (Throwable e) {
                    e.printStackTrace(); // For debug purposes
                }
            });
        }
        threadExecutor.shutdown();

        if (!threadExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
            threadExecutor.shutdownNow();
            if (!threadExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                fail("Thread execution was interrupted");
            }
        }

        assertEquals(workersCnt, successCnt.get(), "All workers must be executed successfully!");
    }

    @Test
    public void testDefaultGroups() {
        List<Group> groups = groupService.getGroups();
        assertEquals(6, groups.size());
        assertCollectionEquals(Stream.of("Administrators", "Analysts", "Deployers", "Developers", "Testers", "Viewers")
                .collect(Collectors.toList()), groups, Group::getName);

        Map<String, Group> mappedGroups = groups.stream()
                .collect(Collectors.toMap(Group::getName, Function.identity()));

        assertCollectionEquals(Stream.of("ADMIN").collect(Collectors.toList()),
                mappedGroups.get("Administrators").getPrivileges(),
                Privilege::getName);

        assertCollectionEquals(Collections.emptyList(),
                mappedGroups.get("Analysts").getPrivileges(),
                Privilege::getName);

        assertCollectionEquals(Collections.emptyList(),
                mappedGroups.get("Deployers").getPrivileges(),
                Privilege::getName);

        assertCollectionEquals(Collections.emptyList(),
                mappedGroups.get("Developers").getPrivileges(),
                Privilege::getName);

        assertCollectionEquals(Collections.emptyList(),
                mappedGroups.get("Testers").getPrivileges(),
                Privilege::getName);

        assertCollectionEquals(Collections.emptyList(),
                mappedGroups.get("Viewers").getPrivileges(),
                Privilege::getName);

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(7, queryCount.getSelect());
        assertEquals(7, queryCount.getTotal());
    }

    @Test
    public void testEmpty() {
        assertFalse(userService.existsByName("Foo"));

        assertTrue(externalGroupService.findAllForUser("Foo").isEmpty());
        assertTrue(externalGroupService.findMatchedForUser("Foo").isEmpty());
        assertTrue(externalGroupService.findNotMatchedForUser("Foo").isEmpty());

        assertEquals(0, externalGroupService.countAllForUser("Foo"));
        assertEquals(0, externalGroupService.countMatchedForUser("Foo"));
        assertEquals(0, externalGroupService.countNotMatchedForUser("Foo"));

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(7, queryCount.getSelect());
        assertEquals(7, queryCount.getTotal());
    }

    @Test
    public void testSearch() {
        initOneUser();
        initSecondUser();

        Set<Privilege> privileges = generatePrivilege(11, "Analysts", "Deployers");
        externalGroupService.mergeAllForUser("jdoe", privileges);
        externalGroupService.mergeAllForUser("jsmith", generatePrivilege(13));
        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(4, queryCount.getInsert());
        assertEquals(4, queryCount.getDelete());
        assertEquals(10, queryCount.getTotal());

        QueryCountHolder.clear();
        List<Group> extGroups = externalGroupService.findAllByName("GROUP_1", 10);
        assertCollectionEquals(Stream.of("GROUP_1", "GROUP_10", "GROUP_11", "GROUP_12").collect(Collectors.toList()),
                extGroups,
                Group::getName);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());

        QueryCountHolder.clear();
        extGroups = externalGroupService.findAllByName("GROUP_1", 2);
        assertCollectionEquals(Stream.of("GROUP_1", "GROUP_10").collect(Collectors.toList()),
                extGroups,
                Group::getName);
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getSelect());
        assertEquals(1, queryCount.getTotal());
    }

    @Test
    public void testSaveLongGroupName() {
        initOneUser();
        externalGroupService.mergeAllForUser("jdoe", Collections.singleton(new SimplePrivilege("foo-bar".repeat(9))));
        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getInsert());
        assertEquals(2, queryCount.getDelete());
        assertEquals(5, queryCount.getTotal());

        QueryCountHolder.clear();
        groupService.addGroup("foo-bar".repeat(9), "Long Group Name");
        queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getInsert());
        assertEquals(1, queryCount.getTotal());
    }

    @Test
    public void testExists() {
        groupService.addGroup("foo", "Foo");
        assertTrue(groupService.existsByName("foo"));
        assertFalse(groupService.existsByName("bar"));
    }

    @Test
    void testCountUsersInGroup() {
        initOneUser();
        initSecondUser();

        externalGroupService.mergeAllForUser("jdoe", Stream.of("foo", "baz")
                .map(SimplePrivilege::new)
                .collect(Collectors.toList()));

        externalGroupService.mergeAllForUser("jsmith", Stream.of( "baz")
                .map(SimplePrivilege::new)
                .collect(Collectors.toList()));

        userManagementService.updateAuthorities("jdoe", Set.of("Analysts", "Developers"));
        userManagementService.updateAuthorities("jsmith", Set.of("Developers"));

        QueryCountHolder.clear();
        assertEquals(2, externalGroupService.countUsersInGroup("baz"));
        assertEquals(1, externalGroupService.countUsersInGroup("foo"));
        assertEquals(0, externalGroupService.countUsersInGroup("UNKNOWN"));

        assertEquals(2, groupService.countUsersInGroup("Developers"));
        assertEquals(1, groupService.countUsersInGroup("Analysts"));
        assertEquals(0, groupService.countUsersInGroup("UNKNOWN"));

        var queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(6, queryCount.getSelect());
        assertEquals(6, queryCount.getTotal());
    }

    private static <T, R> void assertCollectionEquals(Collection<R> expected,
                                                      Collection<T> actual,
                                                      Function<T, R> attr) {
        List<R> transformedActual = actual.stream().map(attr).collect(Collectors.toList());
        List<R> rest = new ArrayList<>(transformedActual);
        rest.removeAll(expected);
        if (!rest.isEmpty()) {
            fail(String.format("Unexpected items: %s",
                    rest.stream().map(Object::toString).collect(Collectors.joining(" ,"))));
        }

        rest = new ArrayList<>(expected);
        rest.removeAll(transformedActual);
        if (!rest.isEmpty()) {
            fail(String.format("Missed expected items: %s",
                    rest.stream().map(Object::toString).collect(Collectors.joining(" ,"))));
        }
    }

    private Set<Privilege> generatePrivilege(int count, String... defaultGroups) {
        Set<Privilege> res = new HashSet<>();
        Stream.of(defaultGroups).map(SimplePrivilege::new).forEach(res::add);
        for (int i = 0; i < count; i++) {
            String name = String.format(RND_GROUP, i);
            res.add(new SimplePrivilege(name));
        }
        return Collections.unmodifiableSet(res);
    }

    private void initOneUser() {
        userService.addUser("jdoe", "Joe", "Doe", "qwerty", "jdoe@test", "Joe Doe");
        QueryCountHolder.clear();
    }

    private void initSecondUser() {
        userService.addUser("jsmith", "John", "Smith", "qwerty", "jsmith@test", "John Smith");
        QueryCountHolder.clear();
    }

}
