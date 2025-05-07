package org.openl.rules.webstudio.security;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.acls.model.Permission;

import org.openl.rules.common.CommonVersion;
import org.openl.rules.common.ProjectException;
import org.openl.rules.project.abstraction.ADeploymentProject;
import org.openl.rules.project.abstraction.AProject;
import org.openl.rules.repository.api.Repository;
import org.openl.rules.workspace.dtr.DesignTimeRepository;
import org.openl.rules.workspace.dtr.DesignTimeRepositoryListener;
import org.openl.rules.workspace.dtr.RepositoryException;
import org.openl.security.acl.permission.AclPermission;
import org.openl.security.acl.repository.RepositoryAclService;
import org.openl.security.acl.repository.SecuredRepositoryFactory;

public class SecureDesignTimeRepositoryImpl implements DesignTimeRepository {

    private DesignTimeRepository designTimeRepository;

    private RepositoryAclService designRepositoryAclService;
    private RepositoryAclService deployConfigRepositoryAclService;

    public RepositoryAclService getDesignRepositoryAclService() {
        return designRepositoryAclService;
    }

    public void setDesignRepositoryAclService(RepositoryAclService designRepositoryAclService) {
        this.designRepositoryAclService = designRepositoryAclService;
    }

    public RepositoryAclService getDeployConfigRepositoryAclService() {
        return deployConfigRepositoryAclService;
    }

    public void setDeployConfigRepositoryAclService(RepositoryAclService deployConfigRepositoryAclService) {
        this.deployConfigRepositoryAclService = deployConfigRepositoryAclService;
    }

    public DesignTimeRepository getDesignTimeRepository() {
        return designTimeRepository;
    }

    public void setDesignTimeRepository(DesignTimeRepository designTimeRepository) {
        this.designTimeRepository = designTimeRepository;
    }

    @Override
    public List<Repository> getRepositories() {
        return designTimeRepository.getRepositories()
                .stream()
                .filter(e -> designRepositoryAclService
                        .isGranted(e.getId(), null, List.of(AclPermission.READ, AclPermission.CREATE))
                        || isGrantedToAnyProject(e.getId(), List.of(AclPermission.READ))
                )
                .map(e -> SecuredRepositoryFactory.wrapToSecureRepo(e, getDesignRepositoryAclService()))
                .collect(Collectors.toList());
    }

    private boolean isGrantedToAnyProject(String repoId, List<Permission> permissions) {
        return designTimeRepository.getProjects(repoId).stream()
                .anyMatch(project -> designRepositoryAclService.isGranted(project, permissions));
    }

    @Override
    public Repository getRepository(String id) {
        return SecuredRepositoryFactory.wrapToSecureRepo(designTimeRepository.getRepository(id),
                getDesignRepositoryAclService());
    }

    @Override
    public AProject getProject(String repositoryId, String name) throws ProjectException {
        AProject project = designTimeRepository.getProject(repositoryId, name);
        if (designRepositoryAclService.isGranted(project, List.of(AclPermission.READ))) {
            return project;
        }
        throw new ProjectException("Access denied");
    }

    @Override
    public AProject getProject(String repositoryId, String name, CommonVersion version) {
        AProject project = designTimeRepository.getProject(repositoryId, name, version);
        if (designRepositoryAclService.isGranted(project, List.of(AclPermission.READ))) {
            return project;
        }
        return null;
    }

    @Override
    public AProject getProjectByPath(String repositoryId,
                                     String branch,
                                     String path,
                                     String version) throws IOException {
        AProject project = designTimeRepository.getProjectByPath(repositoryId, branch, path, version);
        if (designRepositoryAclService.isGranted(project, List.of(AclPermission.READ))) {
            return project;
        }
        throw new AccessDeniedException("Access denied");
    }

    @Override
    public Collection<AProject> getProjects() {
        return designTimeRepository.getProjects()
                .stream()
                .filter(e -> designRepositoryAclService.isGranted(e, List.of(AclPermission.READ)))
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends AProject> getProjects(String repositoryId) {
        return designTimeRepository.getProjects(repositoryId)
                .stream()
                .filter(e -> designRepositoryAclService.isGranted(e, List.of(AclPermission.READ)))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasProject(String repositoryId, String name) {
        return designTimeRepository.hasProject(repositoryId, name);
    }

    @Override
    public ADeploymentProject.Builder createDeploymentConfigurationBuilder(String name) {
        return designTimeRepository.createDeploymentConfigurationBuilder(name);
    }

    @Override
    public List<ADeploymentProject> getDDProjects() throws RepositoryException {
        return designTimeRepository.getDDProjects()
                .stream()
                .filter(e -> deployConfigRepositoryAclService.isGranted(e, List.of(AclPermission.READ)))
                .collect(Collectors.toList());
    }

    @Override
    public void refresh() {
        designTimeRepository.refresh();
    }

    @Override
    public boolean hasDDProject(String name) {
        return designTimeRepository.hasDDProject(name);
    }

    @Override
    public void addListener(DesignTimeRepositoryListener listener) {
        designTimeRepository.addListener(listener);
    }

    @Override
    public void removeListener(DesignTimeRepositoryListener listener) {
        designTimeRepository.removeListener(listener);
    }

    @Override
    public String getRulesLocation() {
        return designTimeRepository.getRulesLocation();
    }

    @Override
    public List<String> getExceptions() {
        return designTimeRepository.getExceptions();
    }

    @Override
    public boolean hasDeployConfigRepo() {
        return designTimeRepository.hasDeployConfigRepo();
    }

    @Override
    public Repository getDeployConfigRepository() {
        return SecuredRepositoryFactory.wrapToSecureRepo(designTimeRepository.getDeployConfigRepository(),
                getDeployConfigRepositoryAclService());
    }

    @Override
    public String getDeployConfigLocation() {
        return designTimeRepository.getDeployConfigLocation();
    }
}
