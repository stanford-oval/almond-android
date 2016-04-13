all:
	make -C node_modules/thingengine-core all-android
	cd node_modules/thingpedia ; npm install --no-optional --only=prod --no-bin-links
	cd node_modules/thingpedia-client ; npm install --no-optional --only=prod --no-bin-links
	cd node_modules/thingpedia-discovery ; npm install --no-optional --only=prod --no-bin-links
	cd node_modules/thingpedia-builtins ; npm install --no-optional --only=prod --no-bin-links
	cd node_modules/thingtalk ; npm install --no-optional --only=prod --no-bin-links
	npm install --no-bin-links
	npm dedupe --no-bin-links
	find -name .bin -type d -exec rm -fr '{}' ';'
