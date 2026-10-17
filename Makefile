.PHONY: check lint lint-workflows test build release run shot emu

check: lint test

lint-workflows:
	bash scripts/lint-workflows.sh

lint:
	./gradlew --quiet :app:lintDebug :app:checkDomainPurity

test:
	./gradlew --quiet :app:testDebugUnitTest

build:
	./gradlew --quiet :app:assembleDebug

release:
	./gradlew --quiet :app:assembleRelease

run: build
	adb install -r app/build/outputs/apk/debug/app-debug.apk
	adb shell am start -n rs.budget/.MainActivity
	sleep 3
	$(MAKE) shot

shot:
	adb exec-out screencap -p > /tmp/budget.png
	@echo "screenshot: /tmp/budget.png"

emu:
	$(HOME)/Android/Sdk/emulator/emulator -avd budget -no-window -no-audio -no-boot-anim -gpu host
