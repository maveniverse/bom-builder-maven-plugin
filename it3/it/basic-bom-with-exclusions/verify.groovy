 File file = new File(basedir, "target/bom-pom.xml")
 def line
 def foundExclusion = false
 file.withReader { reader ->
    while ((line = reader.readLine())!=null) {
       if (line.contains("<exclusion>")) {
         foundExclusion = true
         break
       }
    }
 }
 if (!foundExclusion) {
    println("bom-pom.xml does not contain correct <exclusion> element")
    return false
 }
