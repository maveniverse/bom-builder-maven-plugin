 File file = new File(basedir, "target/bom-pom.xml")
 def line
 def foundType = false
 file.withReader { reader ->
    while ((line = reader.readLine())!=null) {
       if (line.contains("<type>pom</type>")) {
         foundType = true
         break
       }
    }
 }
 if (!foundType) {
    println("bom-pom.xml does not contain correct <type/> element")
    return false
 }
