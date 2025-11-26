
#!/bin/sh
# Fail on any error
set -e

APIHOST=ibm-license-service-reporter-reporter.apps.itz-u0m8pt.infra01-lb.fra02.techzone.ibm.com
CMDBHOST=itsm-reporter.apps.itz-u0m8pt.infra01-lb.fra02.techzone.ibm.com
ITSMHOST=itsm-reporter.apps.itz-u0m8pt.infra01-lb.fra02.techzone.ibm.com

export DEBUG=true
export INSECURE=true
export BOUCHON=false

# Optional: Print debug info
echo "Starting Java application..."
echo "Arguments: $APIHOST $CMDBHOST $ITSMHOST"

# JVM options (disable hostname verification, truststore config)
JAVA_OPTS="-Djdk.internal.httpclient.disableHostnameVerification=true -cp gson-2.13.2.jar"
#JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStoreType=PKCS12"
#JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=/app/truststore.pkcs12"
#JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStorePassword=password"

# Run the fat JAR (preferred)
exec java $JAVA_OPTS -jar target/apiILS.jar $APIHOST $CMDBHOST $ITSMHOST 

