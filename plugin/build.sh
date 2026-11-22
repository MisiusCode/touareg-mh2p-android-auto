#!/bin/bash
# Build VWAndroidAutoNav.jar.
#   LSD_JAR  the unit's HMI classes: lsd.jxe from a dump, converted with jxe2jar (not in this repo)
#   OSGI_JAR an OSGi framework jar (jxe2jar ships one in libs/)
#   JDK8     a JDK 8 home (javac must accept -source 1.4 -target 1.4)
set -e
: "${LSD_JAR:?set LSD_JAR}" "${OSGI_JAR:?set OSGI_JAR}" "${JDK8:?set JDK8}"
cd "$(dirname "$0")"
VER=$(sed -n 's/.*VERSION = "\(.*\)".*/\1/p' src/vwaa/Activator.java)
rm -rf out && mkdir out
"$JDK8/bin/javac" -nowarn -source 1.4 -target 1.4 -cp "$LSD_JAR:$OSGI_JAR" -d out src/vwaa/*.java
printf 'Manifest-Version: 1.0\r\nImplementation-Title: VWAndroidAutoNav\r\nImplementation-Version: %s\r\n\r\n' "$VER" > out/MANIFEST.MF
(cd out && "$JDK8/bin/jar" cfm ../dist/VWAndroidAutoNav-$VER.jar MANIFEST.MF vwaa/*.class)
echo "built dist/VWAndroidAutoNav-$VER.jar"
