all:
	make -C node_modules/thingengine-core all-android
	cd node_modules/thingpedia ; npm install --no-optional --only=prod
	cd node_modules/thingpedia-client ; npm install --no-optional --only=prod
	cd node_modules/thingpedia-discovery ; npm install --no-optional --only=prod
	cd node_modules/thingtalk ; npm install --no-optional --only=prod
	# remove duplicate copy of thingtalk
	# we cannot rely on npm dedupe because we're playing submodule tricks
	rm -fr node_modules/sabrina/node_modules/thingtalk
	npm install
	npm dedupe
