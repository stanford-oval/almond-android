#!/bin/bash

# OSX specific hack
export PATH=/usr/local/bin:$PATH

outputdir="$1"
projectdir=".."

if test -z "$outputdir" ; then
	echo "Missing destination directory"
	exit 1
fi

set -e
set -x

browserify --node -e $projectdir/jsapp/app.js -o $outputdir/app.js
