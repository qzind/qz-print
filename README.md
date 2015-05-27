qz-print
========

Free browser applet for sending documents and raw commands to a printer.

##Compilation

 1. Install dependencies
  ```bash
  sudo apt-get install git openjdk-7-jdk ant nsis makeself
  ```
  
 2. Clone the repository
 ```bash
 git clone -b 1.9 https://github.com/qzind/qz-print
 ```
 
 3. Compile
 ```bash
 cd qz-print/qz-print
 ant
 ```
 
 4. Package
 ```bash
 ant nsis       # <-- Windows installer
 ant pkgbuild   # <-- Apple installer
 ant makeself   # <-- Linux installer
 ```
 
========
 
##IntelliJ
 
 1. Download and install JDK 1.7 or higher from [oracle.com/technetwork](http://www.oracle.com/technetwork/java/javase/downloads/)
 2. Download and install IntelliJ from https://www.jetbrains.com/idea/
 3. Launch IntelliJ
 4. When prompted, click **Check out from Version Control (GitHub)**
 
   **Host:** `github.com`<br>
   **Auth type:** `password`<br>
   **Login:** `<github username>`<br>
   **Password:** `<github password>`<br>

 5. Clone Repository
 
   **Git Repository URL:** `https://github.com/qzind/qz-print`<br>
   **Parent Directory:** `<leave default, usually "C:\Users\username\IdeaProjects">`<br>
   **Directory Name:** `<leave default, "qz-print">`<br>
   Note, if the Parent Directory doesn't exist, create it.

 6. Open the project
 7. Switch to project view using `ALT + 1`
 8. Click **File, Project Structure**
   * Verify Project SDK is correct.  This must be 1.7 or higher.
   * If `<No SDK>`, click New, JDK and browse to the appropriate install location, e.g. `C:\Program Files\Java\jdk1.7.0_XX`
 9. Keeping the Project Struture Window open. Navigate to **Modules, Dependencies Tab**
   * If `plugin.jar` shows an error, remove it and re-add it:
     * Click plugin.jar, Click `-` to remove
     * Click `+` to add, JARs or Directories
     * Browse to the SDK location, e.g. `C:\Program Files\Java\jdk1.7.0_XX\jre\lib\plugin.jar`
     * Make sure `plugin.jar` is at the top of the Dependencies listing, use arrows if needed, OK
 10. From the Project Explorer, Navigate to
   * `qz-print`, `src`, `qz`, `ws`, `PrintWebSocketServer`
   * Right Click, Run
   * On Windows, a firewall prompt may appear, click Run
 11. Exit PrintWebSocketServer by locating it in the System Tray, Right Click, Exit
   * Alternately, you can click **Stop** within IntelliJ from bottom left "Run" tab
 12. To enable HTTPS support in IntelliJ
   * FIXME
