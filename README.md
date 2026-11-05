# Tomcat clock

A Tomcat application that shows a large digital clock, `HH:MM:SS`, using the time of the host running the application.

## Build and deploy

### Build

To build the application in the /target directory use :

````bash
cd <root-location-where-repo-was-cloned>
mvn -B package
````

To create a new container image using the dockerfile use :

````bash
buildah bud --tls-verify=false --layers --pull --isolation=chroot -f Dockerfile -t <image-repo-location>
buildah push <image-repo-location>
````

### Deploy

Deployment resources are in the /deployment directory. The container image location needs to be updated to reference your specific repository and the version number that you push above.

## Experimenting

If you want to experiment with different Hardened image versions then simply update the reference in the dockerfile and rebuild, creating a new image tag each time.
