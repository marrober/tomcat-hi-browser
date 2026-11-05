#!/usr/bin/env python3
"""Append ROOT.war onto the amd64 hardened Tomcat image.

Buildah recompresses base layers when it pushes, so ACS cannot match them.
This copies the published amd64 blobs with skopeo and adds one layer. The
base layer digests in the new manifest are the registry digests unchanged.

Example:
  python3 scripts/append-war.py --war target/tomcat-hi-browser.war --layout /tmp/tomcat-clock
  skopeo copy oci:/tmp/tomcat-clock:tomcat-hi-browser docker://quay.io/marrober/tomcat-hi-browser:4
"""

import argparse
import gzip
import hashlib
import io
import json
import shutil
import subprocess
import tarfile
from pathlib import Path

DEFAULT_BASE = "registry.access.redhat.com/hi/tomcat:11.0.27"
WAR_IN_IMAGE = "usr/local/tomcat/webapps/ROOT.war"
LAYER_OWNER = 65532
TAG = "tomcat-hi-browser"


def run(command):
    subprocess.run(command, check=True)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def digest_of(data):
    return "sha256:" + sha256(data)


def repo_name(reference):
    reference = reference.removeprefix("docker://")
    name = reference.split("@", 1)[0]
    last = name.rsplit("/", 1)[-1]
    if ":" in last:
        name = name.rsplit(":", 1)[0]
    return name


def skopeo_raw(reference):
    return subprocess.check_output(["skopeo", "inspect", "--raw", reference])


def amd64_reference(base):
    document = json.loads(skopeo_raw("docker://" + base))
    manifests = document.get("manifests")
    if not manifests:
        return base
    for manifest in manifests:
        platform = manifest.get("platform") or {}
        if platform.get("os") == "linux" and platform.get("architecture") == "amd64":
            return repo_name(base) + "@" + manifest["digest"]
    raise SystemExit("no linux/amd64 manifest in " + base)


def blob_path(layout, digest):
    algorithm, hexdigest = digest.split(":", 1)
    return layout / "blobs" / algorithm / hexdigest


def read_json_blob(layout, digest):
    return json.loads(blob_path(layout, digest).read_bytes())


def write_blob(layout, data):
    digest = digest_of(data)
    path = blob_path(layout, digest)
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_bytes(data)
    elif path.read_bytes() != data:
        raise SystemExit("digest collision for " + digest)
    return digest


def war_layer(war_path):
    payload = war_path.read_bytes()
    tar_buffer = io.BytesIO()
    with tarfile.open(fileobj=tar_buffer, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        info = tarfile.TarInfo(WAR_IN_IMAGE)
        info.size = len(payload)
        info.uid = LAYER_OWNER
        info.gid = LAYER_OWNER
        info.mode = 0o644
        info.mtime = 0
        archive.addfile(info, io.BytesIO(payload))
    tar_bytes = tar_buffer.getvalue()
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode="wb", mtime=0) as gzip_file:
        gzip_file.write(tar_bytes)
    return tar_bytes, compressed.getvalue()


def tagged_manifest(index):
    for descriptor in index["manifests"]:
        annotations = descriptor.get("annotations") or {}
        if annotations.get("org.opencontainers.image.ref.name") == TAG:
            return descriptor
    if len(index["manifests"]) == 1:
        return index["manifests"][0]
    raise SystemExit("OCI layout has no manifest named " + TAG)


def append_layer(layout, war_path, base_name, base_digest):
    index = json.loads((layout / "index.json").read_bytes())
    descriptor = tagged_manifest(index)
    manifest = read_json_blob(layout, descriptor["digest"])
    config = read_json_blob(layout, manifest["config"]["digest"])
    original_layers = [layer["digest"] for layer in manifest["layers"]]

    tar_bytes, gzip_bytes = war_layer(war_path)
    layer_digest = write_blob(layout, gzip_bytes)
    config["rootfs"]["diff_ids"].append(digest_of(tar_bytes))
    history = config.setdefault("history", [])
    history.append({
        "created_by": "COPY --chown=65532:65532 ROOT.war /usr/local/tomcat/webapps/ROOT.war",
    })
    config_bytes = json.dumps(config, separators=(",", ":")).encode("utf-8")
    config_digest = write_blob(layout, config_bytes)

    media_type = manifest["layers"][-1]["mediaType"]
    manifest["layers"].append({
        "mediaType": media_type,
        "digest": layer_digest,
        "size": len(gzip_bytes),
    })
    manifest["config"] = {
        "mediaType": manifest["config"]["mediaType"],
        "digest": config_digest,
        "size": len(config_bytes),
    }
    annotations = manifest.setdefault("annotations", {})
    annotations["org.opencontainers.image.base.name"] = base_name
    annotations["org.opencontainers.image.base.digest"] = base_digest
    manifest_bytes = json.dumps(manifest, separators=(",", ":")).encode("utf-8")
    manifest_digest = write_blob(layout, manifest_bytes)

    descriptor["mediaType"] = manifest.get("mediaType", descriptor.get("mediaType"))
    descriptor["digest"] = manifest_digest
    descriptor["size"] = len(manifest_bytes)
    descriptor.setdefault("platform", {"architecture": "amd64", "os": "linux"})
    descriptor.setdefault("annotations", {})["org.opencontainers.image.ref.name"] = TAG
    (layout / "index.json").write_bytes(json.dumps(index, indent=2).encode("utf-8") + b"\n")
    return original_layers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE, help="base image name and tag")
    parser.add_argument("--war", required=True, type=Path, help="WAR file to place at ROOT.war")
    parser.add_argument("--layout", required=True, type=Path, help="OCI layout directory to create")
    args = parser.parse_args()
    if not args.war.is_file():
        raise SystemExit("WAR not found: " + str(args.war))
    if shutil.which("skopeo") is None:
        raise SystemExit("skopeo is required")

    base_ref = amd64_reference(args.base)
    base_digest = base_ref.split("@", 1)[1]
    if args.layout.exists():
        shutil.rmtree(args.layout)
    run([
        "skopeo", "copy",
        "docker://" + base_ref,
        "oci:" + str(args.layout) + ":" + TAG,
    ])
    base_layers = append_layer(args.layout, args.war, args.base.removeprefix("docker://"), base_digest)

    produced = json.loads(skopeo_raw("oci:" + str(args.layout) + ":" + TAG))
    produced_layers = [layer["digest"] for layer in produced["layers"]]
    if produced_layers[:len(base_layers)] != base_layers:
        raise SystemExit("base layer digests were rewritten")
    if len(produced_layers) != len(base_layers) + 1:
        raise SystemExit("expected one added layer")
    print("base layers preserved: " + str(len(base_layers)))
    print("first layer: " + produced_layers[0])
    print("oci:" + str(args.layout) + ":" + TAG)


if __name__ == "__main__":
    main()
