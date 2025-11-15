#!/bin/zsh
SESSIONNAME="scala-full-stack"
tmux has-session -t $SESSIONNAME 2>/dev/null
if [ $? != 0 ]; then
    tmux new-session -d -s $SESSIONNAME
    tmux send-keys -t $SESSIONNAME "sbt \"~app / fastOptJS\"  " C-m
    tmux split-window -v -t $SESSIONNAME
    tmux send-keys -t $SESSIONNAME:0.1 "sleep 3 && sbt \"~server / run\" " C-m
    tmux split-window -v -t $SESSIONNAME
    tmux send-keys -t $SESSIONNAME:0.2 "cd app && npm run start" 
fi
tmux attach-session -t $SESSIONNAME