#!/usr/bin/env sh
sbt "app / fastOptJS"
cd app
npm install
npm run build-prod
cd ..

sbt "server / assembly"
docker build . -t scalafullstack

docker run -d -p 8080:4041 scalafullstack

