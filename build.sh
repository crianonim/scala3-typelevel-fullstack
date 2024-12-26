
sbt "app / fastOptJS"

cd app

npm install

npm run build-prod

sbt "server / run "

