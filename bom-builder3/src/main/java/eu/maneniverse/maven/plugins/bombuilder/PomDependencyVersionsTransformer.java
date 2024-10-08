package eu.maneniverse.maven.plugins.bombuilder;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;

class PomDependencyVersionsTransformer {

    public Model transformPomModel(Model model) {
        Model pomModel = model.clone();
        DependencyManagement depMgmt = pomModel.getDependencyManagement();
        Map<String, String> groupIdArtifactIdVersions = new TreeMap<>();
        Map<String, String> groupIdArtifactIdPropertyNames = new TreeMap<>();
        Map<String, String> groupIdVersions = new TreeMap<>();
        Map<String, Set<String>> groupIdArtifactIds = new TreeMap<>();
        for (Dependency dependency : depMgmt.getDependencies()) {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String groupIdArtifactId = groupId + ":" + artifactId;
            groupIdArtifactIdVersions.put(groupIdArtifactId, dependency.getVersion());
            groupIdVersions.put(groupId, dependency.getVersion());

            Set<String> artifactIds = groupIdArtifactIds.computeIfAbsent(groupId, k -> new HashSet<>());
            artifactIds.add(artifactId);
        }

        Properties properties = pomModel.getProperties();
        for (Map.Entry<String, String> groupVersion : groupIdVersions.entrySet()) {
            String groupId = groupVersion.getKey();
            Set<String> artifactIds = groupIdArtifactIds.get(groupId);
            if (artifactIds.size() == 1
                    || allArtifactsInGroupHaveSameVersion(groupId, groupIdArtifactIdVersions, artifactIds)) {
                String propertyName = "version." + groupId;
                properties.setProperty(propertyName, groupVersion.getValue());
                for (String artifactId : artifactIds) {
                    String groupIdArtifactId = groupId + ":" + artifactId;
                    groupIdArtifactIdPropertyNames.put(groupIdArtifactId, propertyName);
                }
            } else {
                for (String artifactId : artifactIds) {
                    String groupIdArtifactId = groupId + ":" + artifactId;
                    String propertyName = "version." + groupId + "." + artifactId;
                    groupIdArtifactIdPropertyNames.put(groupIdArtifactId, propertyName);
                    properties.setProperty(propertyName, groupIdArtifactIdVersions.get(groupIdArtifactId));
                }
            }
        }
        for (Dependency dependency : depMgmt.getDependencies()) {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String groupIdArtifactId = groupId + ":" + artifactId;
            String propertyName = groupIdArtifactIdPropertyNames.get(groupIdArtifactId);
            dependency.setVersion("${" + propertyName + "}");
        }
        return pomModel;
    }

    private boolean allArtifactsInGroupHaveSameVersion(
            String groupId, Map<String, String> groupIdArtifactIdVersions, Set<String> artifactIds) {
        String version = null;
        for (String artifactId : artifactIds) {
            String groupIdArtifactId = groupId + ":" + artifactId;
            if (version == null) {
                version = groupIdArtifactIdVersions.get(groupIdArtifactId);
            } else {
                if (!version.equals(groupIdArtifactIdVersions.get(groupIdArtifactId))) {
                    return false;
                }
            }
        }
        return true;
    }
}
