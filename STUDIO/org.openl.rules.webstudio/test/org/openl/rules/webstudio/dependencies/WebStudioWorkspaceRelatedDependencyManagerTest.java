package org.openl.rules.webstudio.dependencies;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import org.openl.dependency.DependencyType;
import org.openl.dependency.IDependencyManager;
import org.openl.dependency.ResolvedDependency;
import org.openl.exception.OpenLCompilationException;
import org.openl.rules.project.instantiation.AbstractDependencyManager;
import org.openl.rules.project.instantiation.SimpleProjectEngineFactory;
import org.openl.rules.project.resolving.ProjectResolvingException;
import org.openl.syntax.code.Dependency;
import org.openl.syntax.impl.IdentifierNode;

public class WebStudioWorkspaceRelatedDependencyManagerTest {

    public static class WebStudioWorkspaceRelatedSimpleProjectEngineFactoryBuilder<T> extends SimpleProjectEngineFactory.SimpleProjectEngineFactoryBuilder<T> {
        @Override
        public WebStudioWorkspaceRelatedSimpleProjectEngineFactory<T> build() {
            if (project == null || project.isEmpty()) {
                throw new IllegalArgumentException("project cannot be null or empty");
            }
            File projectFile = new File(project);
            File[] dependencies = getProjectDependencies();
            return new WebStudioWorkspaceRelatedSimpleProjectEngineFactory<>(projectFile,
                    dependencies,
                    classLoader,
                    interfaceClass,
                    externalParameters,
                    executionMode);
        }
    }

    public static class WebStudioWorkspaceRelatedSimpleProjectEngineFactory<T> extends SimpleProjectEngineFactory<T> {
        public WebStudioWorkspaceRelatedSimpleProjectEngineFactory(File project,
                                                                   File[] projectDependencies,
                                                                   ClassLoader classLoader,
                                                                   Class<T> interfaceClass,
                                                                   Map<String, Object> externalParameters,
                                                                   boolean executionMode) {
            super(project,
                    projectDependencies,
                    classLoader,
                    interfaceClass,
                    externalParameters,
                    executionMode);
        }

        @Override
        protected IDependencyManager buildDependencyManager() throws ProjectResolvingException {
            return new WebStudioWorkspaceRelatedDependencyManager(buildProjectDescriptors(),
                    classLoader,
                    isExecutionMode(),
                    externalParameters,
                    true);

        }
    }

    @Test
    public void test() throws ProjectResolvingException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 5);

        final WebStudioWorkspaceRelatedSimpleProjectEngineFactory<?> factory = (WebStudioWorkspaceRelatedSimpleProjectEngineFactory<?>) (new WebStudioWorkspaceRelatedSimpleProjectEngineFactoryBuilder<>()
                .setProject("test/rules/compilation")
                .setExecutionMode(false)
                .build());

        WebStudioWorkspaceRelatedDependencyManager webStudioWorkspaceRelatedDependencyManager = (WebStudioWorkspaceRelatedDependencyManager) factory
                .getDependencyManager();
        Random rnd = new Random();

        final int times = 200;
        CountDownLatch countDownLatch = new CountDownLatch(times);
        int c = 0;
        for (int i = 0; i < times; i++) {
            if (i % 4 == 0) {
                c++;
            }
        }
        CountDownLatch countDownLatchLambda = new CountDownLatch(c);
        AtomicLong count = new AtomicLong(0);
        AtomicLong count1 = new AtomicLong(0);
        for (int i = 0; i < times; i++) {
            final int i0 = i;
            executorService.submit(() -> {
                try {
                    if (i0 % 4 != 0) {
                        int p = (rnd.nextInt(3) + 1);
                        Collection<ResolvedDependency> resolvedDependencies;
                        try {
                            resolvedDependencies = webStudioWorkspaceRelatedDependencyManager
                                    .resolveDependency(
                                            new Dependency(DependencyType.MODULE,
                                                    new IdentifierNode(null, null, "Module" + p, null)),
                                            false);
                        } catch (OpenLCompilationException e) {
                            throw new RuntimeException(e);
                        }
                        webStudioWorkspaceRelatedDependencyManager.reset(resolvedDependencies.iterator().next());
                        try {
                            webStudioWorkspaceRelatedDependencyManager
                                    .loadDependency(resolvedDependencies.iterator().next());
                        } catch (OpenLCompilationException e) {
                            e.printStackTrace();
                        }
                    } else {
                        count1.incrementAndGet();
                        try {
                            Thread.sleep(rnd.nextInt(50));
                        } catch (InterruptedException ignored) {
                        }
                        try {
                            webStudioWorkspaceRelatedDependencyManager.loadDependencyAsync(
                                    AbstractDependencyManager.buildResolvedDependency(factory.getProjectDescriptor()),
                                    (e) -> {
                                        try {
                                            if (!e.getCompiledOpenClass().hasErrors()) {
                                                count.incrementAndGet();
                                            }
                                        } finally {
                                            countDownLatchLambda.countDown();
                                        }
                                    });
                        } catch (ProjectResolvingException e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        countDownLatchLambda.await();
        assertEquals(count.get(), count1.get(), "Unexpected number of successfully compilations");
    }
}
