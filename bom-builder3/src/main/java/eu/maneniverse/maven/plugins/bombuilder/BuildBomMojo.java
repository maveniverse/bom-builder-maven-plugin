package eu.maneniverse.maven.plugins.bombuilder;

import static org.codehaus.plexus.util.StringUtils.trim;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.StringUtils;

/**
 * Generates a BOM based on the project/reactor and dependencies. The generated BOM may be attached to project w/
 * classifier (for Maven 4 consumers) or it may replace a given subproject POM (if it is packaging=pom and
 * have no subprojects).
 * <p>
 * This Mojo is affected if it needs "whole reactor"
 * but reactor is limited in any way (ie -r or alike option used).
 */
@Mojo(
        name = "build-bom",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class BuildBomMojo extends AbstractMojo {

    private static final String VERSION_PROPERTY_PREFIX = "version.";

    /**
     * BOM parent GAV, in form for {@code G:A:V}. If specified, the GAV will be set as parent of generated BOM.
     * See also {@link #useProjectParentAsParent}.
     */
    @Parameter
    private String bomParentGav;

    /**
     * BOM groupId, by default current project groupId.
     */
    @Parameter(required = true, property = "bom.groupId", defaultValue = "${project.groupId}")
    private String bomGroupId;

    /**
     * BOM artifactId, by default current project artifactId.
     */
    @Parameter(required = true, property = "bom.artifactId", defaultValue = "${project.artifactId}")
    private String bomArtifactId;

    /**
     * BOM version, by default current project version.
     */
    @Parameter(required = true, property = "bom.version", defaultValue = "${project.version}")
    private String bomVersion;

    /**
     * BOM name.
     */
    @Parameter(property = "bom.name")
    private String bomName;

    /**
     * BOM description.
     */
    @Parameter(property = "bom.description")
    private String bomDescription;

    /**
     * BOM classifier, optional. If not specified, and {@link #attach} is set, will <em>replace current module POM</em>.
     */
    @Parameter(property = "bom.classifier")
    private String bomClassifier;

    /**
     * Whether to add collected versions to BOM properties.
     *
     * @see #usePropertiesForVersion
     */
    @Parameter
    private boolean addVersionProperties;

    /**
     * Whether to use properties to specify dependency versions in BOM. This will also add properties to BOM with
     * dependency versions.
     *
     * @see #addVersionProperties
     */
    @Parameter(property = "bom.usePropertiesForVersion")
    boolean usePropertiesForVersion;

    /**
     * BOM output file. If relative, is resolved from {@code ${project.build}} directory.
     */
    @Parameter(defaultValue = "bom-pom.xml")
    String outputFilename;

    /**
     * Whether the BOM should include the dependency exclusions that
     * are present in the source POM.  By default, the exclusions
     * will not be copied to the new BOM.
     */
    @Parameter
    private List<BomExclusion> exclusions;

    /**
     * List of dependencies which should be excluded from BOM.
     */
    @Parameter
    private List<DependencyExclusion> dependencyExclusions;

    /**
     * List of dependencies which should be included in BOM. If set, only included ones will be added to BOM.
     *
     * @since 1.2.1
     */
    @Parameter
    private List<DependencyExclusion> dependencyInclusions;

    /**
     * The scope of dependencies getting into BOM.
     *
     * @since 1.1.0
     */
    public enum Scope {
        NONE,
        REACTOR,
        CURRENT_PROJECT
    }

    /**
     * The projects of the reactor to be included in generated BOM. Possible values and their meaning:
     * <ul>
     *     <li>NONE - will result that no reactor project are included in BOM.</li>
     *     <li>REACTOR - will include whole reactor into BOM. <em>Warning: if reactor is any way "limited", it will affect this mojo output!</em></li>
     *     <li>CURRENT_PROJECT - will include only current project into BOM.</li>
     * </ul>
     *
     * Note: see also {@link #includePoms}.
     *
     * @since 1.1.1
     */
    @Parameter(property = "bom.reactorDependencies", defaultValue = "REACTOR")
    Scope reactorDependencies;

    /**
     * The direct dependencies to be included in generated BOM. Possible values and their meaning:
     * <ul>
     *     <li>NONE - will result that no direct dependencies are included in BOM.</li>
     *     <li>REACTOR - will include whole reactor direct dependencies into BOM. <em>Warning: if reactor is any way "limited", it will affect this mojo output!</em></li>
     *     <li>CURRENT_PROJECT - will include direct dependencies of only current project into BOM.</li>
     * </ul>
     *
     * @since 1.1.1
     */
    @Parameter(property = "bom.directDependencies", defaultValue = "NONE")
    Scope directDependencies;

    /**
     * The transitive dependencies to be included in generated BOM. Possible values and their meaning:
     * <ul>
     *     <li>NONE - will result that no transitive dependencies are included in BOM.</li>
     *     <li>REACTOR - will include whole reactor transitive dependencies into BOM. <em>Warning: if reactor is any way "limited", it will affect this mojo output!</em></li>
     *     <li>CURRENT_PROJECT - will include transitive dependencies of only current project into BOM.</li>
     * </ul>
     *
     * @since 1.1.1
     */
    @Parameter(property = "bom.transitiveDependencies", defaultValue = "NONE")
    Scope transitiveDependencies;

    /**
     * Whether generated BOM contain reactor artifacts with packaging "pom" as well, when a {@link #reactorDependencies}
     * value is set that pulls in reactor artifacts.
     */
    @Parameter(property = "bom.includePoms")
    boolean includePoms;

    /**
     * Should the generated BOM use project parent, if applicable, as parent? Ignored if {@link #bomParentGav} specified.
     */
    @Parameter(property = "bom.useProjectParentAsParent")
    boolean useProjectParentAsParent;

    /**
     * Should the generated BOM be attached to project? See {@link #bomClassifier}.
     * Note: if this parameter is {@code true}, the generated BOM will be attached using given classifier OR
     * will replace module POM. To replace, the project must fulfil certain requirements:
     * <ul>
     *     <li>The project must have packaging "pom"</li>
     *     <li>The project must NOT have subprojects (modules)</li>
     * </ul>
     * In case {@link #bomClassifier} is not set, and current project does not fulfil these requirements, the mojo
     * will fail the build.
     */
    @Parameter(property = "bom.attach")
    boolean attach;

    /**
     * The current project
     */
    @Parameter(defaultValue = "${project}")
    MavenProject mavenProject;

    /**
     * All projects from reactor
     */
    @Parameter(defaultValue = "${session.allProjects}")
    List<MavenProject> allProjects;

    @Component
    MavenProjectHelper mavenProjectHelper;

    private final PomDependencyVersionsTransformer versionsTransformer;
    private final ModelWriter modelWriter;

    public BuildBomMojo() {
        this(new ModelWriter(), new PomDependencyVersionsTransformer());
    }

    BuildBomMojo(ModelWriter modelWriter, PomDependencyVersionsTransformer versionsTransformer) {
        this.versionsTransformer = versionsTransformer;
        this.modelWriter = modelWriter;
    }

    @Override
    public void execute() throws MojoExecutionException {
        getLog().debug("Generating BOM");
        Model model = initializeModel();
        addDependencyManagement(model);
        if (usePropertiesForVersion) {
            model = versionsTransformer.transformPomModel(model);
            getLog().debug("Dependencies versions converted to properties");
        }
        Path outputFile = Paths.get(mavenProject.getBuild().getDirectory()).resolve(outputFilename);
        modelWriter.writeModel(model, outputFile.toFile());
        if (attach) {
            if (bomClassifier != null && !bomClassifier.trim().isEmpty()) {
                getLog().debug("Attaching BOM w/ classifier: " + bomClassifier);
                DefaultArtifact artifact = new DefaultArtifact(
                        bomGroupId, bomArtifactId, bomVersion, null, "pom", bomClassifier, new PomArtifactHandler());
                artifact.setFile(outputFile.toFile());
                mavenProject.addAttachedArtifact(artifact);
            } else if (Objects.equals("pom", mavenProject.getPackaging())
                    && mavenProject.getModules().isEmpty()) {
                getLog().debug("Replacing module POM w/ generated BOM");
                mavenProject.setFile(outputFile.toFile());
            } else {
                throw new MojoExecutionException(
                        "Cannot replace project POM: invalid project (packaging=pom w/o modules)");
            }
        }
    }

    private Model initializeModel() throws MojoExecutionException {
        Model pomModel = new Model();
        pomModel.setModelVersion("4.0.0");

        if (bomParentGav != null) {
            String[] gav = bomParentGav.split(":");
            if (gav.length != 3) {
                throw new MojoExecutionException(
                        "BOM parent should be specified as [groupId]:[artifactId]:[version] but is '" + bomParentGav
                                + "'");
            }
            Parent parent = new Parent();
            parent.setGroupId(gav[0]);
            parent.setArtifactId(gav[1]);
            parent.setVersion(gav[2]);
            pomModel.setParent(parent);
        } else if (useProjectParentAsParent && mavenProject.getModel().getParent() != null) {
            pomModel.setParent(mavenProject.getModel().getParent());
            pomModel.getParent().setRelativePath(null);
        }

        pomModel.setGroupId(bomGroupId);
        pomModel.setArtifactId(bomArtifactId);
        pomModel.setVersion(bomVersion);
        pomModel.setPackaging("pom");

        if (bomName != null) {
            pomModel.setName(bomName);
        }
        if (bomDescription != null) {
            pomModel.setDescription(bomDescription);
        }

        if (attach && (bomClassifier == null || bomClassifier.trim().isEmpty())) {
            // standalone main BOM POM; inherit required things for publishing for "this" project
            if (bomName == null) {
                pomModel.setName(mavenProject.getModel().getName());
            }
            if (bomDescription == null) {
                pomModel.setDescription(mavenProject.getModel().getDescription());
            }
            pomModel.setUrl(mavenProject.getModel().getUrl());
            pomModel.setLicenses(mavenProject.getModel().getLicenses());
            pomModel.setDevelopers(mavenProject.getModel().getDevelopers());
            pomModel.setScm(mavenProject.getModel().getScm());
        }

        return pomModel;
    }

    private void addDependencyManagement(Model pomModel) {
        HashSet<Artifact> projectArtifactsSet = new HashSet<>();
        if (reactorDependencies == Scope.REACTOR) {
            for (MavenProject prj : allProjects) {
                if (includePoms || !"pom".equals(prj.getArtifact().getType())) {
                    projectArtifactsSet.add(prj.getArtifact());
                }
            }
        } else if (reactorDependencies == Scope.CURRENT_PROJECT
                && (includePoms || !"pom".equals(mavenProject.getArtifact().getType()))) {
            projectArtifactsSet.add(mavenProject.getArtifact());
        }

        if (directDependencies == Scope.REACTOR) {
            for (MavenProject prj : allProjects) {
                if (includePoms || !"pom".equals(prj.getArtifact().getType())) {
                    projectArtifactsSet.addAll(prj.getDependencyArtifacts());
                }
            }
        } else if (directDependencies == Scope.CURRENT_PROJECT) {
            projectArtifactsSet.addAll(mavenProject.getDependencyArtifacts());
        }

        if (transitiveDependencies == Scope.REACTOR) {
            for (MavenProject prj : allProjects) {
                if (includePoms || !"pom".equals(prj.getArtifact().getType())) {
                    prj.setArtifactFilter(a -> !"test".equals(a.getScope()));
                    projectArtifactsSet.addAll(prj.getArtifacts());
                }
            }
        } else if (transitiveDependencies == Scope.CURRENT_PROJECT) {
            mavenProject.setArtifactFilter(a -> !"test".equals(a.getScope()));
            projectArtifactsSet.addAll(mavenProject.getArtifacts());
        }

        // Sort the artifacts for readability
        ArrayList<Artifact> projectArtifacts = new ArrayList<>(projectArtifactsSet);
        Collections.sort(projectArtifacts);

        LinkedHashMap<String, String> versionProperties = new LinkedHashMap<>();
        DependencyManagement depMgmt = new DependencyManagement();
        for (Artifact artifact : projectArtifacts) {
            if (!isIncludedDependency(artifact)) {
                continue;
            }
            if (isExcludedDependency(artifact)) {
                continue;
            }

            String versionPropertyName = VERSION_PROPERTY_PREFIX + artifact.getGroupId();
            if (versionProperties.get(versionPropertyName) != null
                    && !versionProperties.get(versionPropertyName).equals(artifact.getVersion())) {
                versionPropertyName = VERSION_PROPERTY_PREFIX + artifact.getGroupId() + "." + artifact.getArtifactId();
            }
            versionProperties.put(versionPropertyName, artifact.getVersion());

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
            Properties props = pomModel.getProperties();
            for (Map.Entry<String, String> entry : versionProperties.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue());
            }
        }
        getLog().debug("Added " + projectArtifacts.size() + " dependencies.");
    }

    boolean isIncludedDependency(Artifact artifact) {
        if (dependencyInclusions == null || dependencyInclusions.isEmpty()) {
            return true;
        }
        for (DependencyExclusion inclusion : dependencyInclusions) {
            if (matchesExcludedDependency(artifact, inclusion)) {
                getLog().debug("Artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId()
                        + " matches included dependency " + inclusion.getGroupId() + ":" + inclusion.getArtifactId());
                return true;
            }
        }
        return false;
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

    static class PomArtifactHandler implements ArtifactHandler {
        PomArtifactHandler() {}

        public String getClassifier() {
            return null;
        }

        public String getDirectory() {
            return null;
        }

        public String getExtension() {
            return "pom";
        }

        public String getLanguage() {
            return "none";
        }

        public String getPackaging() {
            return "pom";
        }

        public boolean isAddedToClasspath() {
            return false;
        }

        public boolean isIncludesDependencies() {
            return false;
        }
    }
}
