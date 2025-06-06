package org.openl.rules.repository.zip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import org.openl.util.FileUtils;
import org.openl.util.RuntimeExceptionWrapper;

/**
 * Read only implementation of Jar Repository to support deploying of jars from classpath as it is without
 * unzipping to temporary directories.</br>
 *
 * <p>
 * NOTE: This repository type doesn't support write actions!
 * </p>
 *
 * @author Vladyslav Pikus
 */
public class JarLocalRepository extends AbstractArchiveRepository {

    private static final String PROJECT_DESCRIPTOR_FILE = "rules.xml";
    private static final String DEPLOYMENT_DESCRIPTOR_XML_FILE = "deployment.xml";
    private static final String DEPLOYMENT_DESCRIPTOR_YAML_FILE = "deployment.yaml";

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public void initialize() {
        final Map<String, Path> localStorage = new HashMap<>();
        final Consumer<Resource> collector = res -> {
            try {
                final URI uri = res.getURI();
                final Path path = toPath(uri);
                final String name = FileUtils.getBaseName(path.getFileName().toString());
                var existed = localStorage.put(name, path);
                if (existed != null && !existed.equals(path)) {
                    throw new IllegalStateException(String.format("The resources '%s' and '%s' conflict for the same '%s' name.", existed, path, name));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to initialize a repository.", e);
            }
        };

        try {
            getResources(PROJECT_DESCRIPTOR_FILE).forEach(collector);
            getResources(DEPLOYMENT_DESCRIPTOR_XML_FILE).forEach(collector);
            getResources(DEPLOYMENT_DESCRIPTOR_YAML_FILE).forEach(collector);
            Stream<Resource> archives;
            try {
                archives = Stream.of(resourceResolver.getResources("/openl/*.zip"));
            } catch (FileNotFoundException ignored) {
                archives = Stream.empty();// OK
            }
            archives.forEach(collector);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize a repository.", e);
        }

        Path root = findCommonParentPath(localStorage.values());
        if (root == null) {
            root = Paths.get(System.getProperty("java.io.tmpdir")); // just a stab to prevent NPE
        }

        setStorage(localStorage);
        setRoot(root);
    }

    private Stream<Resource> getResources(String fileName) throws IOException {
        String locationPattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + fileName;
        return Stream.of(resourceResolver.getResources(locationPattern));
    }

    private static Path toPath(URI uri) {
        if ("jar".equals(uri.getScheme())) {
            String path = uri.getRawSchemeSpecificPart();
            int sep = path.indexOf("!/");
            if (sep > -1) {
                path = path.substring(0, sep);
            }
            try {
                URI uriToZip = new URI(path);
                if (uriToZip.getSchemeSpecificPart().contains("%")) {
                    //FIXME workaround to fix double URI encoding for URIs from ZipPath
                    try {
                        uriToZip = new URI(uriToZip.getScheme() + ":" + uriToZip.getSchemeSpecificPart());
                    } catch (URISyntaxException ignored) {
                        //it's ok
                    }
                }
                return Paths.get(uriToZip);
            } catch (URISyntaxException e) {
                throw RuntimeExceptionWrapper.wrap(e);
            }
        } else if ("file".equals(uri.getScheme())) {
            return Paths.get(uri);
        }
        throw new IllegalArgumentException("Invalid URI scheme.");
    }

}
