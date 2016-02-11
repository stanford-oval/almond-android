all: all-js
	./gradlew build

all-js:
	make -C jsapp all

clean:
	make -C jsapp clean

run-mock: all-js
	test -d home || mkdir home/
	cd home/ ; jx ../app/src/main/assets/jxcore_mock.js
