#!/bin/bash
# Simulated drive on a PC: stand-ins for the unit's services, fake Android Auto events.
# Needs the same LSD_JAR / OSGI_JAR / JDK8 as build.sh, and ../out from build.sh.
# The plugin waits 45 s before it starts, so the run takes about 80 s.
set -e
: "${LSD_JAR:?set LSD_JAR}" "${OSGI_JAR:?set OSGI_JAR}" "${JDK8:?set JDK8}"
cd "$(dirname "$0")"
rm -rf cls && mkdir cls
"$JDK8/bin/javac" -encoding UTF-8 -nowarn -cp "../out:$OSGI_JAR:$LSD_JAR" -d cls $(find stubs harness -name '*.java')
: > /tmp/vwaa.log; rm -f /tmp/vwaa_off
"$JDK8/bin/java" -Dfile.encoding=UTF-8 -cp "cls:../out:$OSGI_JAR:$LSD_JAR" test.Harness
echo "--- plugin log: /tmp/vwaa.log"
