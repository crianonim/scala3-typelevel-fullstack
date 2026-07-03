#!/usr/bin/env sh
set -e

# Deploy the server image to a Raspberry Pi on the local network.
#
# Usage: ./deploy-pi.sh [ssh-host]
#   ssh-host defaults to "pi" (configure it in ~/.ssh/config).
#
# Builds the fat JAR + Docker image locally for the Pi's architecture,
# then streams the image over SSH into the Pi's Docker daemon — no
# registry required.

PI_HOST="${1:-crianonim@raspberry5.local}"
IMAGE="scalafullstack"
# Pi 3/4/5 on 64-bit OS = linux/arm64. Older / 32-bit Pi = linux/arm/v7.
PLATFORM="${PLATFORM:-linux/arm64}"

# Preflight: fail fast (before the multi-minute build) if we can't reach the
# Pi's Docker daemon over SSH. Catches missing key auth, SSH not enabled, wrong
# user/host, or the user lacking docker-group permissions.
echo "==> Checking SSH + Docker access on ${PI_HOST}"
if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "${PI_HOST}" "docker info" >/dev/null 2>&1; then
  echo "ERROR: cannot run 'docker info' on ${PI_HOST} via SSH." >&2
  echo "  Check, in order:" >&2
  echo "  1. Key auth works passwordlessly:  ssh ${PI_HOST} true" >&2
  echo "     (if it prompts for a password, run: ssh-copy-id ${PI_HOST})" >&2
  echo "  2. SSH is enabled on the Pi:        sudo systemctl enable --now ssh" >&2
  echo "  3. User can run docker without sudo: ssh ${PI_HOST} 'sudo usermod -aG docker \$USER'" >&2
  echo "     (then reconnect for the group change to take effect)" >&2
  exit 1
fi

echo "==> Building frontend + fat JAR"
sbt "app / fastOptJS"
cd app
rm -rf .parcel-cache
rm -f dist/*
npm install
npm run build-prod
cd ..
sbt "server / assembly"

echo "==> Building image for ${PLATFORM}"
# --load places the built image in the local Docker image store so we can save it.
docker buildx build --platform "${PLATFORM}" --load -t "${IMAGE}" .

echo "==> Shipping image to ${PI_HOST} and (re)starting container"
docker save "${IMAGE}" | ssh "${PI_HOST}" "docker load"
ssh "${PI_HOST}" "docker rm -f ${IMAGE} 2>/dev/null; docker run -d --name ${IMAGE} -p 8080:8080 ${IMAGE}"

echo "==> Done. Server should be reachable at http://${PI_HOST}:8080/tables"