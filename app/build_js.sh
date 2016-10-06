#!/bin/bash

BABEL_PLUGINS="
babel-preset-es2015
babel-plugin-transform-es2015-template-literals
babel-plugin-transform-es2015-literals
babel-plugin-transform-es2015-function-name
babel-plugin-transform-es2015-arrow-functions
babel-plugin-transform-es2015-block-scoped-functions
babel-plugin-transform-es2015-classes
babel-plugin-transform-es2015-object-super
babel-plugin-transform-es2015-shorthand-properties
babel-plugin-transform-es2015-duplicate-keys
babel-plugin-transform-es2015-computed-properties
babel-plugin-transform-es2015-for-of
babel-plugin-transform-es2015-sticky-regex
babel-plugin-transform-es2015-unicode-regex
babel-plugin-check-es2015-constants
babel-plugin-transform-es2015-spread
babel-plugin-transform-es2015-parameters
babel-plugin-transform-es2015-destructuring
babel-plugin-transform-es2015-block-scoping
babel-plugin-transform-es2015-typeof-symbol
babel-plugin-transform-es2015-modules-commonjs
babel-plugin-transform-regenerator"

install_babel() {
	test -d "$projectdir/node_modules" || mkdir ../node_modules
	(cd "$projectdir/" ; npm install babel-cli $BABEL_PLUGINS )
}



transpile_js() {
	rm -fr "$jxcoredir"
	mkdir "$jxcoredir"
	$BABEL --preset es2015 --ignore 'jsapp/node_modules/*/test/*' -D -d "${jxcoredir}" "$projectdir/jsapp/"
}

install_deps() {
	( cd "${jxcoredir}/" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingengine-core" ; npm install --no-optional --only=prod --no-bin-links ; npm run compile-mo )
	( cd "${jxcoredir}/node_modules/thingpedia" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingpedia-discovery" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/thingtalk" ; npm install --no-optional --only=prod --no-bin-links )
	( cd "${jxcoredir}/node_modules/sabrina" ; npm install --no-optional --only=prod --no-bin-links ; npm run compile-mo )
	( cd "${jxcoredir}/" ; npm dedupe --no-bin-links )
	rm -fr "${jxcoredir}/node_modules/sabrina/node_modules/thingtalk"
	find "${jxcoredir}/" -name .bin -type d -exec rm -fr '{}' ';'
	find "${jxcoredir}/" -type f \! -name \*.js \! -name \*.json \! -name \*.jade  \! -name \*.css \! -name \*.sql \! -name \*.mo \! -name \*.cert \! -iname \*LICENSE\* \! -iname \*COPYING\* \! -iname \*README\* -delete
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
