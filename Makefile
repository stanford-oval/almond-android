all: all-js
	./gradlew build

assetdir = app/src/main/assets
jxcoredir = $(assetdir)/jxcore

empty =
space = $(empty) $(empty)
comma = ,

BABEL_IGNORE = \
	jsapp/node_modules/adt/macros/index.js \
	jsapp/node_modules/errno/cli.js \
	jsapp/node_modules/lokijs/benchmark/nedb.js \
	jsapp/node_modules/*/test/*

install-js:
	test -d ./node_modules || mkdir ./node_modules
	npm install babel babel-preset-es2015
	./node_modules/.bin/babel --preset es2015 --ignore $(subst $(space),$(comma),$(BABEL_IGNORE)) -D -d $(jxcoredir) ./jsapp/
	find $(jxcoredir)/ -name .bin -type d -exec rm -fr '{}' ';'
	find $(jxcoredir)/ -type f \! -name \*.js \! -name \*.json \! -name \*.sql \! -name \*.cert \! -iname \*LICENSE\* \! -iname \*COPYING\* \! -iname \*README\* -delete
	find $(jxcoredir)/ -type d -empty -delete

all-js:
	make -C jsapp all
	make install-js

clean:
	make -C jsapp clean

run-mock: all-js
	test -d home || mkdir home/
	cd home/ ; jx ../$(assetdir)/jxcore_mock.js
