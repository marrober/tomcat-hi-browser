FROM registry.access.redhat.com/hi/tomcat:11.0.27

# The hardened image runs as uid 65532. Copy as root so the archive is
# written into the documented webapps directory, then hand it to that user.
USER 0
COPY --chown=65532:65532 target/tomcat-hi-browser.war /usr/local/tomcat/webapps/ROOT.war
USER 65532

EXPOSE 8080
