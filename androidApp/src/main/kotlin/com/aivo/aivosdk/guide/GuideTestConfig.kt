/*
 * This directory previously contained interactive developer guide tests.
 *
 * These tests have been moved to the proper test source sets:
 *   - Unit tests: sdk/src/commonTest/kotlin/
 *   - JVM integration tests: samples/cli-jvm/
 *
 * To verify SDK behaviour against a real provider, use the CLI sample:
 *   ./gradlew :samples:cli-jvm:run
 *
 * To run unit tests with FakeLlmProvider:
 *   ./gradlew :sdk:test
 *
 * See docs/DEVELOPER_GUIDE.md for the full developer guide.
 */
