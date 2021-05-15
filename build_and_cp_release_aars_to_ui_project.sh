#!/bin/bash

exit_if_failed() {
    if [ $? -ne 0 ]; then
	echo "ABORT: Previous step failed"
	exit 1
    fi
}

./gradlew assembleRelease
exit_if_failed

cp libbluetooth_gnss_service/build/outputs/aar/libbluetooth_gnss_service-release.aar ../bluetooth_gnss/android/libbluetooth_gnss_service/
exit_if_failed

cp libecodroidbluetooth/build/outputs/aar/libecodroidbluetooth-release.aar ../bluetooth_gnss/android/libecodroidbluetooth/
exit_if_failed

cp libecodroidgnss_parse/build/outputs/aar/libecodroidgnss_parse-release.aar ../bluetooth_gnss/android/libecodroidgnss_parse/
exit_if_failed

./gradlew assembleDebug
exit_if_failed

cp libbluetooth_gnss_service/build/outputs/aar/libbluetooth_gnss_service-debug.aar ../bluetooth_gnss/android/libbluetooth_gnss_service/
exit_if_failed

cp libecodroidbluetooth/build/outputs/aar/libecodroidbluetooth-debug.aar ../bluetooth_gnss/android/libecodroidbluetooth/
exit_if_failed

cp libecodroidgnss_parse/build/outputs/aar/libecodroidgnss_parse-debug.aar ../bluetooth_gnss/android/libecodroidgnss_parse/
exit_if_failed

echo "copied all aars to ui project (../bluetooth_gnss)"
echo SUCCESS

