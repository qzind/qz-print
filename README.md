qz-print
========

Free browser applet for sending documents and raw commands to a printer.

**Compilation Instructions**

 1. Install depencies
  ```bash
  sudo apt-get install git open-jdk-7-jdk ant nsis makeself
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
 ant makeself   # <-- Linux installer
 ```
