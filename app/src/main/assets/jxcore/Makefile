all:
	make -C node_modules/thingengine-core all-android
	cd node_modules/thingpedia ; npm install --no-optional --only=prod
	cd node_modules/thingpedia-client ; npm install --no-optional --only=prod
	cd node_modules/thingpedia-discovery ; npm install --no-optional --only=prod
	cd node_modules/thingtalk ; npm install --no-optional --only=prod
	npm install
	npm dedupe
	find -name .bin -type d -exec rm -fr '{}' ';'
