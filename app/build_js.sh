#!/bin/bash

# OSX specific hack
export PATH=/usr/local/bin:$PATH

outputdir=`realpath "$1"`
projectdir=".."

set -e
set -x

for mod in almond thingtalk thingengine-core ; do
	podir="$projectdir/jsapp/node_modules/$mod/po"
	for pofile in $podir/*.po ; do
		node $projectdir/jsapp/build_translations.js "$pofile" > "$podir/"$(basename $pofile .po)".json"
	done
done

(cd $projectdir/jsapp ;
npm install
#./node_modules/.bin/browserify -t [ eslintify --passthrough warnings ] --node -e app.js -o $outputdir/app.js
./node_modules/.bin/browserify --node -e app.js -o $outputdir/app.js
)
node -c $outputdir/app.js
