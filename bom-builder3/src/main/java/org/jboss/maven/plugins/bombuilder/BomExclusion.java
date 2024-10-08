package org.jboss.maven.plugins.bombuilder;

/**
 * A class to represent exclusion configuration for the BOM
 * builder.
 *
 */
public class BomExclusion {

    private String dependencyGroupId;

    private String dependencyArtifactId;

    private String exclusionGroupId;

    private String exclusionArtifactId;

    public String getDependencyGroupId() {
        return dependencyGroupId;
    }

    public void setDependencyGroupId(String dependencyGroupId) {
        this.dependencyGroupId = dependencyGroupId;
    }

    public String getDependencyArtifactId() {
        return dependencyArtifactId;
    }

    public void setDependencyArtifactId(String dependencyArtifactId) {
        this.dependencyArtifactId = dependencyArtifactId;
    }

    public String getExclusionGroupId() {
        return exclusionGroupId;
    }

    public void setExclusionGroupId(String exclusionGroupId) {
        this.exclusionGroupId = exclusionGroupId;
    }

    public String getExclusionArtifactId() {
        return exclusionArtifactId;
    }

    public void setExclusionArtifactId(String exclusionArtifactId) {
        this.exclusionArtifactId = exclusionArtifactId;
    }
}
