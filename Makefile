NULL =

all: all-js
	./gradlew build

assetdir = app/src/main/assets
jxcoredir = $(assetdir)/jxcore

empty =
space = $(empty) $(empty)
comma = ,

BABEL_IGNORE = \
	jsapp/node_modules/*/test/* \
	$(NULL)

node_modules/babel-preset-es2015:
	test -d ./node_modules || mkdir ./node_modules
	npm install babel babel-preset-es2015

$(jxcoredir): node_modules/babel-preset-es2015
	./node_modules/.bin/babel --preset es2015 --ignore $(subst $(space),$(comma),$(BABEL_IGNORE)) -D -d $(jxcoredir) ./jsapp/

all-js: $(jxcoredir)
	cd $(jxcoredir)/ ; npm install --no-optional --only=prod --no-bin-links
	cd $(jxcoredir)/node_modules/thingengine-core ; npm install --no-optional --only=prod --no-bin-links
	cd $(jxcoredir)/node_modules/thingpedia ; npm install --no-optional --only=prod --no-bin-links
	cd $(jxcoredir)/node_modules/thingpedia-discovery ; npm install --no-optional --only=prod --no-bin-links
	cd $(jxcoredir)/node_modules/thingtalk ; npm install --no-optional --only=prod --no-bin-links
	cd $(jxcoredir)/ ; npm dedupe --no-bin-links
	find $(jxcoredir)/ -name .bin -type d -exec rm -fr '{}' ';'
	find $(jxcoredir)/ -type f \! -name \*.js \! -name \*.json \! -name \*.sql \! -name \*.cert \! -iname \*LICENSE\* \! -iname \*COPYING\* \! -iname \*README\* -delete
	find $(jxcoredir)/ -type d -empty -delete

clean-js:
	rm -fr $(jxcoredir)

run-mock: all-js
	test -d home || mkdir home/
	cd home/ ; jx ../$(assetdir)/jxcore_mock.js
