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

for mod in almond thingengine-core ; do
	podir="$projectdir/jsapp/node_modules/$mod/po"
	for pofile in $podir/*.po ; do
		node $projectdir/jsapp/build_translations.js "$pofile" > "$podir/"$(basename $pofile .po)".json"
	done
done

# assume levelup and levelgraph exist, they won't actually be loaded
# at runtime
browserify --node -e $projectdir/jsapp/app.js -x levelup -x levelgraph -o $outputdir/app.js
node -c $projectdir/jsapp/app.js
