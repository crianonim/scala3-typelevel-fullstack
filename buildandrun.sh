#!/usr/bin/env sh

sbt "app / fastOptJS"
cd app
rm -rf .parcel-cache
rm dist/*
npm install
npm run build-prod
cd ..

sbt "server / assembly"
docker build  --no-cache . -t scalafullstack

docker run -d -p 8080:4041 scalafullstack

