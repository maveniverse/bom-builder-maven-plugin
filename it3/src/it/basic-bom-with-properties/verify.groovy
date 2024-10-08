 File file = new File(basedir, "target/bom-pom.xml")
 def line
 def foundProperty = false
 file.withReader { reader ->
    while ((line = reader.readLine())!=null) {
       if (line.contains("<version.junit>4.8</version.junit>")) {
         foundProperty = true
         break
       }
    }
 }
 if (!foundProperty) {
    println("bom-pom.xml does not contain correct junit version property")
    return false
 }
