package eu.maneniverse.maven.plugins.bombuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

public class PomDependencyVersionsTransformerTest {
    @Test
    public void testDependencyVersionsAreSpecifiedByProperties() {
        PomDependencyVersionsTransformer transformer = new PomDependencyVersionsTransformer();
        Model pomModel = createPomModel();
        Dependency dependency = createDependency("groupId", "artifactId", "version");
        addDependency(pomModel, dependency);

        Model transformedModel = transformer.transformPomModel(pomModel);

        assertEquals(1, transformedModel.getProperties().size());
        String versionKey = createKey(dependency);
        assertEquals(dependency.getVersion(), transformedModel.getProperties().get(versionKey));
        assertEquals(
                "${" + versionKey + "}",
                transformedModel
                        .getDependencyManagement()
                        .getDependencies()
                        .get(0)
                        .getVersion());
    }

    @Test
    public void testDependencyVersionIsSpecifiedByPropertyWithKeyIncludingArtifactId() {
        PomDependencyVersionsTransformer transformer = new PomDependencyVersionsTransformer();
        Model pomModel = createPomModel();
        Dependency dependency1 = createDependency("groupId", "artifactId1", "version1");
        addDependency(pomModel, dependency1);
        Dependency dependency2 = createDependency("groupId", "artifactId2", "version2");
        addDependency(pomModel, dependency2);

        Model transformedModel = transformer.transformPomModel(pomModel);

        assertEquals(2, transformedModel.getProperties().size());
        String versionKey1 = createKeyIncludingArtifactId(dependency1);
        assertEquals(dependency1.getVersion(), transformedModel.getProperties().get(versionKey1));
        assertEquals(
                "${" + versionKey1 + "}",
                transformedModel
                        .getDependencyManagement()
                        .getDependencies()
                        .get(0)
                        .getVersion());

        String versionKey2 = createKeyIncludingArtifactId(dependency2);
        assertEquals(dependency2.getVersion(), transformedModel.getProperties().get(versionKey2));
        assertEquals(
                "${" + versionKey2 + "}",
                transformedModel
                        .getDependencyManagement()
                        .getDependencies()
                        .get(1)
                        .getVersion());
    }

    // FIXME one groupId with same versions, but configuration requires property for given artifactId

    private String createKey(Dependency dependency) {
        return "version." + dependency.getGroupId();
    }

    private String createKeyIncludingArtifactId(Dependency dependency) {
        return "version." + dependency.getGroupId() + "." + dependency.getArtifactId();
    }

    private Dependency createDependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private void addDependency(Model pomModel, Dependency dependency) {
        pomModel.getDependencyManagement().addDependency(dependency);
    }

    private Model createPomModel() {
        Model model = new Model();
        DependencyManagement dependencyManagement = new DependencyManagement();
        model.setDependencyManagement(dependencyManagement);
        return model;
    }
}
