#!/bin/bash


stop-all.sh;

start-all.sh;

sleep 2;

hadoop job -list;
