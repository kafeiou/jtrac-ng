# ==============================================================================
# Stage 1: Build JTrac Web Application Archive (WAR)
# ==============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy Maven descriptor and source tree
COPY pom.xml .
COPY src ./src

# Build JTrac WAR skipping tests
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime Image (Eclipse Temurin 21 + Jetty 12.1 EE10)
# ==============================================================================
FROM jetty:12.1-jre21-eclipse-temurin

USER root

# Install fontconfig, multilingual fonts (CJK, European diacritics, Vietnamese), gosu, and curl
RUN apt-get update && apt-get install -y --no-install-recommends \
    fontconfig \
    fonts-noto-cjk \
    fonts-noto-core \
    fonts-dejavu-core \
    gosu \
    curl \
    && fc-cache -fv \
    && rm -rf /var/lib/apt/lists/*

# Configure Jetty Base with ee10-deploy and ee10-webapp modules for jakarta.servlet 6.0 support
WORKDIR $JETTY_BASE
RUN java -jar "$JETTY_HOME/start.jar" --add-modules=ee10-deploy,ee10-webapp

# Create persistent data directory with jetty ownership
RUN mkdir -p /jtrac-data && chown -R jetty:jetty /jtrac-data

# Deploy WAR artifact as ROOT.war
COPY --from=builder /build/target/jtrac.war $JETTY_BASE/webapps/ROOT.war

# Install custom entrypoint script
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Environment variables for JVM and JTrac home directory
ENV JTRAC_HOME=/jtrac-data \
    JAVA_OPTIONS="-Djtrac.home=/jtrac-data -Dfile.encoding=UTF-8 -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0"

EXPOSE 8080

VOLUME ["/jtrac-data"]

ENTRYPOINT ["/entrypoint.sh"]
CMD ["java", "-jar", "/usr/local/jetty/start.jar"]
