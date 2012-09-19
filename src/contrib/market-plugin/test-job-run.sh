#!/bin/bash
PROJECT_HOME=../../..
#echo $PROJECT_HOME

hadoop_examples_jar=$PROJECT_HOME/build/hadoop-examples-1.1-market.jar
#./test-modif-job-client.sh;

hadoop dfs -rmr /terasort-input
hadoop jar $hadoop_examples_jar teragen 100 /terasort-input





