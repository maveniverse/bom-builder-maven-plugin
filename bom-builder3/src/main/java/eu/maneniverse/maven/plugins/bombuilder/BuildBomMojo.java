package eu.maneniverse.maven.plugins.bombuilder;

import static org.codehaus.plexus.util.StringUtils.trim;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.codehaus.plexus.util.StringUtils;

/**
 * Build a BOM based on the dependencies in a GAV
 */
@Mojo(
        name = "build-bom",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class BuildBomMojo extends AbstractMojo {

    private static final String VERSION_PROPERTY_PREFIX = "version.";
    /**
     * BOM groupId
     */
    @Parameter(required = true, property = "bom.groupId", defaultValue = "${project.groupId}")
    private String bomGroupId;

    /**
     * BOM artifactId
     */
    @Parameter(required = true, property = "bom.artifactId", defaultValue = "${project.artifactId}")
    private String bomArtifactId;

    /**
     * BOM version
     */
    @Parameter(required = true, property = "bom.version", defaultValue = "${project.version}")
    private String bomVersion;

    /**
     * BOM name
     */
    @Parameter(defaultValue = "", property = "bom.name")
    private String bomName;

    /**
     * BOM name
     */
    @Parameter
    private boolean addVersionProperties;

    /**
     * BOM description
     */
    @Parameter(defaultValue = "")
    private String bomDescription;

    /**
     * BOM output file
     */
    @Parameter(defaultValue = "bom-pom.xml")
    String outputFilename;

    /**
     * Whether the BOM should include the dependency exclusions that
     * are present in the source POM.  By default the exclusions
     * will not be copied to the new BOM.
     */
    @Parameter
    private List<BomExclusion> exclusions;

    /**
     * List of dependencies which should not be added to BOM
     */
    @Parameter
    private List<DependencyExclusion> dependencyExclusions;

    /**
     * Whether use properties to specify dependency versions in BOM
     */
    @Parameter
    boolean usePropertiesForVersion;

    @Parameter(property = "bom.useDependencies")
    boolean useDependencies;

    @Parameter(property = "bom.includePoms")
    boolean includePoms;

    /**
     * The current project
     */
    @Component
    MavenProject mavenProject;

    @Parameter(defaultValue = "${session.allProjects}")
    List<MavenProject> allProjects;

    /**
     *
     */
    @Component
    private ModelBuilder modelBuilder;

    /**
     *
     */
    @Component
    private ProjectBuilder projectBuilder;

    private final PomDependencyVersionsTransformer versionsTransformer;
    private final ModelWriter modelWriter;

    public BuildBomMojo() {
        this(new ModelWriter(), new PomDependencyVersionsTransformer());
    }

    public BuildBomMojo(ModelWriter modelWriter, PomDependencyVersionsTransformer versionsTransformer) {
        this.versionsTransformer = versionsTransformer;
        this.modelWriter = modelWriter;
    }

    public void execute() throws MojoExecutionException {
        getLog().debug("Generating BOM");
        Model model = initializeModel();
        addDependencyManagement(model);
        if (usePropertiesForVersion) {
            model = versionsTransformer.transformPomModel(model);
            getLog().debug("Dependencies versions converted to properties");
        }
        modelWriter.writeModel(model, new File(mavenProject.getBuild().getDirectory(), outputFilename));
    }

    private Model initializeModel() {
        Model pomModel = new Model();
        pomModel.setModelVersion("4.0.0");

        pomModel.setGroupId(bomGroupId);
        pomModel.setArtifactId(bomArtifactId);
        pomModel.setVersion(bomVersion);
        pomModel.setPackaging("pom");

        pomModel.setName(bomName);
        pomModel.setDescription(bomDescription);

        pomModel.setProperties(new OrderedProperties());
        pomModel.getProperties().setProperty("project.build.sourceEncoding", "UTF-8");

        return pomModel;
    }

    private void addDependencyManagement(Model pomModel) {
        // Sort the artifacts for readability
        List<Artifact> projectArtifacts = new ArrayList<>();
        if (useDependencies) {
            projectArtifacts.addAll(mavenProject.getArtifacts());
        } else {
            for (MavenProject prj : allProjects) {
                if (includePoms || !"pom".equals(prj.getArtifact().getType())) {
                    projectArtifacts.add(prj.getArtifact());
                }
            }
        }
        Collections.sort(projectArtifacts);

        Properties versionProperties = new Properties();
        DependencyManagement depMgmt = new DependencyManagement();
        for (Artifact artifact : projectArtifacts) {
            if (isExcludedDependency(artifact)) {
                continue;
            }

            String versionPropertyName = VERSION_PROPERTY_PREFIX + artifact.getGroupId();
            if (versionProperties.getProperty(versionPropertyName) != null
                    && !versionProperties.getProperty(versionPropertyName).equals(artifact.getVersion())) {
                versionPropertyName = VERSION_PROPERTY_PREFIX + artifact.getGroupId() + "." + artifact.getArtifactId();
            }
            versionProperties.setProperty(versionPropertyName, artifact.getVersion());

            Dependency dep = new Dependency();
            dep.setGroupId(artifact.getGroupId());
            dep.setArtifactId(artifact.getArtifactId());
            dep.setVersion(artifact.getVersion());
            if (!StringUtils.isEmpty(artifact.getClassifier())) {
                dep.setClassifier(artifact.getClassifier());
            }
            if (!StringUtils.isEmpty(artifact.getType())) {
                dep.setType(artifact.getType());
            }
            if (exclusions != null) {
                applyExclusions(artifact, dep);
            }
            depMgmt.addDependency(dep);
        }
        pomModel.setDependencyManagement(depMgmt);
        if (addVersionProperties) {
            pomModel.getProperties().putAll(versionProperties);
        }
        getLog().debug("Added " + projectArtifacts.size() + " dependencies.");
    }

    boolean isExcludedDependency(Artifact artifact) {
        if (dependencyExclusions == null || dependencyExclusions.isEmpty()) {
            return false;
        }
        for (DependencyExclusion exclusion : dependencyExclusions) {
            if (matchesExcludedDependency(artifact, exclusion)) {
                getLog().debug("Artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId()
                        + " matches excluded dependency " + exclusion.getGroupId() + ":" + exclusion.getArtifactId());
                return true;
            }
        }
        return false;
    }

    boolean matchesExcludedDependency(Artifact artifact, DependencyExclusion exclusion) {
        String groupId = defaultAndTrim(artifact.getGroupId());
        String artifactId = defaultAndTrim(artifact.getArtifactId());
        String exclusionGroupId = defaultAndTrim(exclusion.getGroupId());
        String exclusionArtifactId = defaultAndTrim(exclusion.getArtifactId());
        boolean groupIdMatched = ("*".equals(exclusionGroupId) || groupId.equals(exclusionGroupId));
        boolean artifactIdMatched = ("*".equals(exclusionArtifactId) || artifactId.equals(exclusionArtifactId));
        return groupIdMatched && artifactIdMatched;
    }

    private String defaultAndTrim(String string) {
        return Objects.toString(trim(string), "");
    }

    private void applyExclusions(Artifact artifact, Dependency dep) {
        for (BomExclusion exclusion : exclusions) {
            if (exclusion.getDependencyGroupId().equals(artifact.getGroupId())
                    && exclusion.getDependencyArtifactId().equals(artifact.getArtifactId())) {
                Exclusion ex = new Exclusion();
                ex.setGroupId(exclusion.getExclusionGroupId());
                ex.setArtifactId(exclusion.getExclusionArtifactId());
                dep.addExclusion(ex);
            }
        }
    }

    static class ModelWriter {

        void writeModel(Model pomModel, File outputFile) throws MojoExecutionException {
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            try (OutputStream outputStream = Files.newOutputStream(outputFile.toPath())) {
                MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                mavenWriter.write(outputStream, pomModel);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to write pom file.", e);
            }
        }
    }
}
