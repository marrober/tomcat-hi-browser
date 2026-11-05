# Tomcat hardened image browser

Java web application that charts known vulnerabilities in the five most recently published plain release tags of `registry.access.redhat.com/hi/tomcat`. It runs on the Red Hat Hardened Tomcat image and refreshes the catalog once an hour.

Plain release tags look like `10.1.60` and `11.0.27`. Tags with `builder` or `jdk` in the name are ignored. Floating aliases such as `latest` and `11` are omitted because they point at one of those releases. Older patch releases remain on the chart after a newer release supersedes them. Counts come from the [Red Hat Hardened Images catalog](https://images.redhat.com/images/tomcat) Grype summaries.

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

The image listens on port 8080 as uid 65532. The page reads `GET /api/vulnerabilities`. `POST /api/vulnerabilities` refreshes immediately; the background task refreshes on its own every hour.

| Variable | Default | Purpose |
| --- | --- | --- |
| `HIBROWSER_API_BASE` | `https://api-hummingbird.hummingbird-project.io/v1` | Hardened Images catalog API |
| `HIBROWSER_IMAGE` | `tomcat` | Catalog image name |
| `HIBROWSER_REPOSITORY` | `registry.access.redhat.com/hi/tomcat` | Repository shown on the page |
| `HIBROWSER_TAG_LIMIT` | `5` | Number of plain release tags to chart |
| `HIBROWSER_REFRESH_MINUTES` | `60` | How often to look for new tags |

## Pipelines as Code

`.tekton/tomcat-hi-browser-on-pr.yaml` runs on pull requests to `main`. It clones the repository, runs `mvn package`, and builds the container with Buildah.

`.tekton/tomcat-hi-browser-on-push.yaml` runs on pushes to `main`. It performs the same build, then pushes the image to the OpenShift internal registry in the pipeline namespace, tagged with the git revision and `latest`.

Both runs are Pipelines as Code `PipelineRun` resources. They pull the `git-clone` task through the `pipelinesascode.tekton.dev/task` annotation. Register the repository with the Pipelines as Code controller (`tkn pac create repository`) so pushes and pull requests create those runs. The pipeline service account needs permission to run Buildah and to push to the namespace image stream.
