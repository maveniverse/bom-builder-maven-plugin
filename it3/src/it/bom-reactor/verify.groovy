 def name = "bom-reactor"
 File file = new File(basedir, "target/bom-pom.xml")
 File expectedFile = new File(basedir, "expected/pom.xml")

 String fileContents = file.getText('UTF-8')
 String expectedFileContents = expectedFile.getText('UTF-8')

 def isDifferent = !fileContents.equals(expectedFileContents)
 if (isDifferent) {
    System.err.println("Generated " + file.absolutePath + " differs from expected " + expectedFile.absolutePath)
    return false
 }
