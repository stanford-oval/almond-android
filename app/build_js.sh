#!/bin/bash

# OSX specific hack
export PATH=/usr/local/bin:$PATH

outputdir=`realpath "$1"`
projectdir=".."

npm_install() {
  if npm --version 2>&1 | grep -q '^5' ; then
     echo "WARNING: you're using npm >= 5.0.0; this is broken (see https://github.com/npm/npm/issues/18209); not installing dependencies"
  else
     npm install
  fi
}

set -e
set -x

for mod in almond thingtalk thingengine-core ; do
	podir="$projectdir/jsapp/node_modules/$mod/po"
	for pofile in $podir/*.po ; do
		node $projectdir/jsapp/build_translations.js "$pofile" > "$podir/"$(basename $pofile .po)".json"
	done
done

(cd $projectdir/jsapp ;
npm_install
./node_modules/.bin/browserify -t [ eslintify --passthrough warnings ] --node -e app.js -o $outputdir/app.js
)
node -c $outputdir/app.js
