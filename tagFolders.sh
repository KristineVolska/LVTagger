#!/bin/bash

cd $(dirname $0)
java -Xmx4G -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json_simple-1.1.jar tagFolders $*