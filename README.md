# Tomcat clock

A Tomcat application that shows the local time as a large digital clock, `HH:MM:SS`.

## Run locally

Requires JDK 17 or newer and Maven.

```bash
mvn -B package
```

The WAR is `target/tomcat-hi-browser.war`. Deploy it as the root application on Tomcat 10 or 11, then open the server root.

Build and run it on the hardened image:

```bash
mvn -B package
podman build -t tomcat-hi-browser .
podman run --rm -p 8080:8080 tomcat-hi-browser
```

Container build instructions

````bash
cd <root-location-where-repo-was-cloned>
mvn -B package
buildah bud --tls-verify=false --pull --isolation=chroot -f Dockerfile -t <image-repo-location>
buildah push <image-repo-location>
````

The image listens on port 8080 as uid 65532. The page reads `GET /api/vulnerabilities`. `POST /api/vulnerabilities` refreshes immediately; the background task refreshes on its own every hour.

| Variable | Default | Purpose |
| --- | --- | --- |
| `HIBROWSER_TAG_LIMIT` | `5` | Number of plain release tags to chart |
| `HIBROWSER_REFRESH_MINUTES` | `60` | How often to look for new tags |

## Pipelines as Code

`.tekton/tomcat-hi-browser-on-pr.yaml` runs on pull requests to `main`. It clones the repository, runs `mvn package`, and builds the container with Buildah.

`.tekton/tomcat-hi-browser-on-push.yaml` runs on pushes to `main`. It performs the same build, then pushes the image to the OpenShift internal registry in the pipeline namespace, tagged with the git revision and `latest`.

Both runs are Pipelines as Code `PipelineRun` resources. They pull the `git-clone` task through the `pipelinesascode.tekton.dev/task` annotation. Register the repository with the Pipelines as Code controller (`tkn pac create repository`) so pushes and pull requests create those runs. The pipeline service account needs permission to run Buildah and to push to the namespace image stream.
