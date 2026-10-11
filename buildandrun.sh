#!/usr/bin/env sh

sbt "app / fastOptJS"
cd app
rm -rf .parcel-cache
rm dist/*
npm install
npm run build-prod
rm -f dist/*.map
cd ..

sbt "server / assembly"
docker build  --no-cache . -t scalafullstack

if [ -f .env.local ]; then
  docker run -d -p 8080:8080 --env-file .env.local scalafullstack
else
  echo "No .env.local found; starting with the bundled local-db defaults."
  docker run -d -p 8080:8080 scalafullstack
fi

