#!/bin/bash -x

cd `rospack find jsk_perception`
mkdir -p launch
mkdir -p template
export ROS_MASTER_URI=http://localhost:12347
#roscore -p 12347 & (roseus ./src/eusmodel_template_gen.l && kill -INT $!)
roseus ./src/eusmodel_template_gen.l
