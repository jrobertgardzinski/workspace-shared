#!/bin/bash

python3 "aggregate_allure.py"
python3 "build_features.py"
./build_javadocs.sh
