import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.Diff

File file = new File(basedir, "bom/target/bom-pom.xml")
File expectedFile = new File(basedir, "expected/pom.xml")

String fileContents = file.getText('UTF-8')
String expectedFileContents = expectedFile.getText('UTF-8')

Diff diff = DiffBuilder.compare(expectedFileContents)
        .withTest(fileContents)
        .build()
def isDifferent = diff.hasDifferences()
if (isDifferent) {
    System.err.println("Generated " + file.absolutePath + " differs from expected " + expectedFile.absolutePath)
    System.err.println(diff)
    return false
 }

// Verify the installed POM in the local repository also contains the generated BOM content.
// This catches Maven 4 consumer POM issues: the consumer POM transformer may install a stripped
// POM that lacks dependencyManagement if the plugin does not handle the transformer correctly.
File installedPom = new File(localRepositoryPath, "reactor-with-bom-module-skinny/bom/1.0-SNAPSHOT/bom-1.0-SNAPSHOT.pom")
if (!installedPom.exists()) {
    System.err.println("Installed POM not found: " + installedPom.absolutePath)
    return false
}
String installedPomContents = installedPom.getText('UTF-8')
if (!installedPomContents.contains('<dependencyManagement>')) {
    System.err.println("Installed POM does not contain <dependencyManagement>: " + installedPom.absolutePath)
    System.err.println("This indicates the Maven 4 consumer POM does not include the generated BOM content.")
    return false
}
