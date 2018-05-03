#!/bin/bash

# OSX specific hack
export PATH=/usr/local/bin:$PATH

outputdir="$1"
projectdir=".."

thingpedia_url="$2"
sempre_url="$3"

install_deps() {
  if ! which yarn >/dev/null 2>&1 ; then
     echo "WARNING: yarn not found, will use npm to install dependencies (this will cause buggy non-deterministic behavior)"
     npm install
  else
     yarn --frozen-lockfile
  fi
}

set -e
set -x

cd $projectdir/jsapp ;
# install dependencies before we build the json (or yarn/npm will overwrite)
install_deps

for mod in almond thingtalk thingengine-core ; do
	podir="./node_modules/$mod/po"
	for pofile in $podir/*.po ; do
		node ./build_translations.js "$pofile" > "$podir/"$(basename $pofile .po)".json"
	done
done

printf '"use strict";\nmodule.exports.SEMPRE_URL = "%s";\nmodule.exports.THINGPEDIA_URL = "%s";\n' "${sempre_url}" "${thingpedia_url}" > ./config.js

./node_modules/.bin/browserify -t [ eslintify --passthrough warnings ] --node -e app.js -o $outputdir/app.js
node -c $outputdir/app.js
