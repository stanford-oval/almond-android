#!/bin/bash

install_babel() {
	test -d "$projectdir/node_modules" || mkdir ../node_modules
	(cd "$projectdir/" ; npm install babel-cli babel-preset-es2015 )
}



transpile_js() {
	$BABEL --preset es2015 --ignore 'jsapp/node_modules/*/test/*' -D -d "${jxcoredir}" "$projectdir/jsapp/"
}

install_deps() {
	( cd "${jxcoredir}/" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingengine-core" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingpedia" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingpedia-discovery" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingtalk" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/sabrina" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/" ; npm dedupe --no-bin-links )
	rm -fr "${jxcoredir}/node_modules/sabrina/node_modules/thingtalk"
	find "${jxcoredir}/" -name .bin -type d -exec rm -fr '{}' ';'
	find "${jxcoredir}/" -type f \! -name \*.js \! -name \*.json \! -name \*.jade  \! -name \*.css \! -name \*.sql \! -name \*.cert \! -iname \*LICENSE\* \! -iname \*COPYING\* \! -iname \*README\* -delete
	find "${jxcoredir}/" -type d -empty -delete
}

jxcoredir="$1"
projectdir=".."
BABEL="$projectdir/node_modules/.bin/babel"

if test -z "$jxcoredir" ; then
	echo "Missing destination directory"
	exit 1
fi

set -e
set -x

test -d ../node_modules/babel-cli || install_babel
transpile_js
install_deps
