#!/bin/bash

# OSX specific hack
export PATH=/usr/local/bin:$PATH

# if building inside a flatpak, set a few things
if test -d /run/host ; then
  yarn () {
    flatpak-spawn --host yarn "$@"
  }
  node () {
    flatpak-spawn --host node "$@"
  }
fi

mkdir -p "$1"
outputdir=`realpath "$1"`
projectdir=".."

thingpedia_url="$2"
sempre_url="$3"
almond_url="$4"

install_deps() {
  yarn --frozen-lockfile
}

set -e
set -x

cd $projectdir/jsapp ;
# install dependencies before we build the json (or yarn/npm will overwrite)
install_deps

for mod in almond-dialog-agent thingtalk thingengine-core ; do
	podir="./node_modules/$mod/po"
	for pofile in $podir/*.po ; do
		node ./build_translations.js "$pofile" > "$podir/"$(basename $pofile .po)".json"
	done
done

node -e "console.log(JSON.stringify(fs.readFileSync(process.argv[1]).toString()))" ./data/thingengine.phone.tt > data/thingengine.phone.tt.json

printf '"use strict";\nmodule.exports.SEMPRE_URL = "%s";\nmodule.exports.THINGPEDIA_URL = "%s";\nmodule.exports.ALMOND_URL = "%s";\n' "${sempre_url}" "${thingpedia_url}" "${almond_url}" > ./config.js

yarn lint
yarn run browserify -t --node -e app.js -o $outputdir/app.js
node -c $outputdir/app.js
