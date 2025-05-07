package org.openl.rules.ruleservice.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.openl.rules.context.IRulesRuntimeContext;
import org.openl.rules.context.RulesRuntimeContextFactory;
import org.openl.rules.ruleservice.management.ServiceManager;
import org.openl.rules.ruleservice.simple.RulesFrontend;

@TestPropertySource(properties = {"production-repository.uri=test-resources/MultiModuleDispatchingTest",
        "production-repository.factory = repo-file"})
@SpringJUnitConfig(locations = {"classpath:openl-ruleservice-beans.xml"})
public class MultiModuleDispatchingTest {
    private static final String SERVICE_NAME = "MultiModuleDispatchingTest_multimodule";

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testMultiModuleService2() throws Exception {
        assertNotNull(applicationContext);
        ServiceManager serviceManager = applicationContext.getBean("serviceManager", ServiceManager.class);
        assertNotNull(serviceManager);
        RulesFrontend frontend = applicationContext.getBean("frontend", RulesFrontend.class);
        IRulesRuntimeContext cxt = RulesRuntimeContextFactory.buildRulesRuntimeContext();

        cxt.setLob("lob1_1");
        assertEquals("Hello1", frontend.execute(SERVICE_NAME, "hello", cxt));
        cxt.setLob("lob2_1");
        assertEquals("Hello2", frontend.execute(SERVICE_NAME, "hello", cxt));
        cxt.setLob("lob3_1");
        assertEquals("Hello3", frontend.execute(SERVICE_NAME, "hello", cxt));

    }

}
