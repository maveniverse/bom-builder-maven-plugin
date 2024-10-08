package org.jboss.maven.plugins.bombuilder;

/**
 * A class to represent dependency exclusion configuration for the BOM
 * builder.
 */
public class DependencyExclusion {

    private String groupId;

    private String artifactId;

    public DependencyExclusion() {}

    public DependencyExclusion(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
}
