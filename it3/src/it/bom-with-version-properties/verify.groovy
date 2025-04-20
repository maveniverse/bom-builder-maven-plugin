import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.Diff

def name = "bom-with-excluded-dependency"
File file = new File(basedir, "target/pom.xml")
File expectedFile = new File(basedir, "expected/pom.xml")

String fileContents = file.getText('UTF-8')
String expectedFileContents = expectedFile.getText('UTF-8')

assert fileContents.contains('<version.junit>4.8</version.junit>')
assert fileContents.contains('<version>${version.junit}</version>')

assert fileContents.contains('<version.org.apache.maven>3.0</version.org.apache.maven>')
assert fileContents.contains('<version>${version.org.apache.maven}</version>')

assert fileContents.contains('<version.org.codehaus.plexus>2.0.4</version.org.codehaus.plexus>')
assert fileContents.contains('<version>${version.org.codehaus.plexus}</version>')

// SKIP this below; mvn4 introduces ordering issues of project/properties
//
// Diff diff = DiffBuilder.compare(expectedFileContents).withTest(fileContents).build()
// def isDifferent = diff.hasDifferences()
// if (isDifferent) {
//     System.err.println("Generated " + file.absolutePath + " differs from expected " + expectedFile.absolutePath)
//     return false
// }
