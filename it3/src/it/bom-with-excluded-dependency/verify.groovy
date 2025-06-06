import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.Diff

File file = new File(basedir, "target/pom.xml")
File expectedFile = new File(basedir, "expected/pom.xml")

String fileContents = file.getText('UTF-8')
String expectedFileContents = expectedFile.getText('UTF-8')

Diff diff = DiffBuilder.compare(expectedFileContents)
        .withTest(fileContents)
        .build()
def isDifferent = diff.hasDifferences()
 if (isDifferent) {
    System.err.println("Generated " + file.absolutePath + " differs from expected " + expectedFile.absolutePath)
    return false
 }
