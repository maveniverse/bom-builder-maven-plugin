BOM Builder Maven 3 Plugin
==========================

A **Maven 3 plugin** to generate a dependency management POM, sometimes called a 
BOM or bill of materials POM.  The plugin reads the set of dependencies in 
the current project, and generates a new POM according configuration,
it will contain dependency management section listing of the dependencies 
as configured (module, or whole reactor, and so on...).

Note: this plugin does not takes stance what BOM is. For some, it is "reactor only",
while for others it is "full stack" with transitive dependencies even. Hence, this
plugin leaves for user to choose which BOM it wants generated, as this is configurable.
Maven team calls first type of BOM "skinny", while latter BOM as "fat".

Note: this plugin is able to generate BOMs with classifiers, but those 
BOMs can be consumed ONLY with Maven 4 (new feature: BOMs with classifiers).

Plugin site with [documentation is here](https://maveniverse.eu/docs/bom_builder_maven_plugin/plugin-documentation/plugin-info.html).

For all covered use cases (there are a LOT!) [check out ITs here](./it3/src/it).

Usage
-----
The plugin is configured in the "plugins" section of the pom.

    <plugins>
      <plugin>
        <groupId>eu.maveniverse.maven.plugins</groupId>
        <artifactId>bom-builder-maven-plugin</artifactId>
        <version>${currentVersion}</version>
        <executions>
          <execution>
            <id>build-bom</id>
            <goals>
              <goal>build-bom</goal>
            </goals>
            <configuration>
              <bomGroupId>org.foo.bom</bomGroupId>
              <bomArtifactId>my-artifacts-bom</bomArtifactId>
              <bomVersion>1.0.0</bomVersion>
              <bomName>My Artifacts BOM</bomName>
              <bomDescription>My Artifacts BOM</bomDescription>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>

To generate Maven 4 consumable BOMs (with classifiers), setup is trivial: just setup a plugin invocation and set
classifier and it will be attached and deployed along with given project.

To generate Maven 3 (and 4) consumable BOM (which must be a main POM artifacts, without classifier), the following steps are needed:
* create a dedicated module (ie. "myproject-bom") in your reactor
* make sure `pom.xml` packaging is set to `pom`
* make sure there are no `modules` entry in `pom.xml` (no child projects)
* add this plugin and configure it in build section of given `pom.xml`, as in [this IT](./it3/src/it/reactor-with-bom-module-fat/bom/pom.xml).
* upon execution, the BOM (as configured) will be generated **and will replace given `pom.xml`**. Hence, nothing else should be present in the `pom.xml`. All the options like parent etc can be controlled via plugin configuration. 

Config Parameters
-----------------
bomGroupId - The groupId to set in the generated BOM
bomArtifactId - The artifactId to set in the generated BOM
bomVersion - The version to set in the generated BOM
bomName - The name to set in the generated BOM
bomDescription - The description to set in the generated BOM
exclusions - A list of exclusions to set in the generated BOM
dependencyExclusions - A list of dependencies which should not be included in the generated BOM

Each exclusion should contain four parameters:
  - dependencyGroupId
  - dependencyArtifactId
  - exclusionGroupId
  - exclusionArtifactId

Each dependency exclusion should contain two parameters:
  - groupId
  - artifactId

Exclusion Config Example
-------------------

    <configuration>
      <bomGroupId>org.test</bomGroupId>
      <bomArtifactId>junit-bom</bomArtifactId>
      <bomVersion>1.0</bomVersion>
      <exclusions>
        <exclusion>
          <dependencyGroupId>junit</dependencyGroupId>
          <dependencyArtifactId>junit</dependencyArtifactId>
          <exclusionGroupId>org.hamcrest</exclusionGroupId>
          <exclusionArtifactId>hamcrest</exclusionArtifactId>
        </exclusion>
      </exclusions>
    </configuration>

The above config will result in POM output that looks similar to the following:

    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.8</version>
      <exclusions>
        <exclusion>
          <artifactId>hamcrest</artifactId>
          <groupId>org.hamcrest</groupId>
        </exclusion>
      </exclusions>
    </dependency>

Dependency Exclusion Config Example
-------------------

    <configuration>
      <bomGroupId>org.test</bomGroupId>
      <bomArtifactId>junit-bom</bomArtifactId>
      <bomVersion>1.0</bomVersion>
      <dependencyExclusions>
        <dependencyExclusion>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
        </dependencyExclusion>
      </dependencyExclusions>
    </configuration>

The above config will result in POM which will not contain junit dependency

You can use * for value of artifactId (or groupId) to exclude all dependencies with given groupId and any artifactId
(or with given artifactId and any groupId)

Using properties for version
----------------------------

    <configuration>
      <bomGroupId>org.test</bomGroupId>
      <bomArtifactId>junit-bom</bomArtifactId>
      <bomVersion>1.0</bomVersion>
      <usePropertiesForVersion>true</usePropertiesForVersion>
    </configuration>

The above config will result in POM where version of dependencies is specified via properties.
