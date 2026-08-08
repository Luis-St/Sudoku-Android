#!/usr/bin/env bash
#
# Drives the daily reminder on a connected device or emulator, so it can be exercised without waiting for
# 09:00 or moving the system clock.
#
# Everything here needs the *debug* build installed: the receiver that `send` talks to lives in src/debug and
# is deliberately absent from release.
#
#   ./send-notifications.sh send      post the reminder now, bypassing the schedule
#   ./send-notifications.sh run       run the real worker now, guards included, and see what it decides
#   ./send-notifications.sh status    what WorkManager currently has enqueued, and when it next runs
#   ./send-notifications.sh jobs      the raw system job behind that schedule
#   ./send-notifications.sh logs      follow the reminder's log output
#
# Every command runs against all connected devices unless -s is given.

set -euo pipefail

PACKAGE="net.luis.sudoku"
SHOW_ACTION="${PACKAGE}.DEBUG_SHOW_REMINDER"
RUN_ACTION="${PACKAGE}.DEBUG_RUN_REMINDER"
DIAGNOSTICS_ACTION="androidx.work.diagnostics.REQUEST_DIAGNOSTICS"

# The header comment is the help text. Read to the first line that is not a comment rather than to a fixed
# line number, so editing the header cannot silently start printing the code below it.
usage() {
	tail -n +2 "$0" | sed -n '/^#/!q; s/^# \{0,1\}//p'
	exit "${1:-0}"
}

# Serial numbers of everything ready to talk to. `adb devices` also lists unauthorized and offline entries,
# which would fail later with a much less obvious message, so only "device" counts.
all_devices() {
	adb devices | awk '$2 == "device" { print $1 }'
}

# Resolves the devices to act on, and explains itself rather than failing silently on none.
resolve_targets() {
	if [[ -n "${SERIAL}" ]]; then
		echo "${SERIAL}"
		return
	fi

	local found
	found="$(all_devices)"
	if [[ -z "${found}" ]]; then
		echo "No device or emulator is connected. Start an AVD, or check 'adb devices'." >&2
		exit 1
	fi
	echo "${found}"
}

# The debug build carries the receiver that `send` needs; a release build on the device looks identical to
# `pm list packages` and then swallows the broadcast without a word.
check_installed() {
	local serial="$1"
	if ! adb -s "${serial}" shell pm list packages | tr -d '\r' | grep -q "^package:${PACKAGE}$"; then
		echo "  ${PACKAGE} is not installed. Run './gradlew installDebug' first." >&2
		return 1
	fi
}

# Posts the notification directly through the debug receiver. This proves what the notification looks like -
# icon, channel, text, tap target - and nothing at all about when it would have fired.
#
# --include-stopped-packages matters: a broadcast is not delivered to an app that has never been launched
# since install, or that has been force stopped, without it.
cmd_send() {
	local serial="$1"
	adb -s "${serial}" shell am broadcast \
		-a "${SHOW_ACTION}" \
		-p "${PACKAGE}" \
		--include-stopped-packages > /dev/null
	echo "  Reminder broadcast sent."
}

# Starts the app process so WorkManager can re-register its jobs with the system, and waits for it to do so.
#
# Needed because the two are not the same thing. WorkManager keeps its own database of enqueued work, which
# survives everything; the JobScheduler entry that actually wakes the device is derived from it, and is
# dropped whenever the app is force stopped - which includes every reinstall from Android Studio. WorkManager
# puts it back through its own ForceStopRunnable the next time the process runs, so a dumpsys taken before
# that says "nothing scheduled" about work that is very much still enqueued.
#
# The diagnostics broadcast is used purely for the side effect of starting the process; what it prints is
# `status`'s business.
wake_workmanager() {
	local serial="$1"
	adb -s "${serial}" shell am broadcast \
		-a "${DIAGNOSTICS_ACTION}" \
		-p "${PACKAGE}" \
		--include-stopped-packages > /dev/null
	sleep 2
}

# Runs the real worker now: the enabled check, the "already reminded today" guard and the "already solved
# today" guard all get a say, so this is the command that tests behaviour rather than appearance. Run it
# twice - the second time should post nothing, which is the guard working.
#
# It goes through the debug receiver rather than `cmd jobscheduler run`, because the scheduled job cannot be
# forced early: WorkManager checks its own periodic schedule before handing over and answers "being executed
# before schedule ... not doing any work and rescheduling for later execution". The receiver enqueues a
# one-shot instead, which runs the same worker with the same guards and leaves the real schedule alone.
#
# Swipe the app away first if the point is to prove it works with the app closed. `am force-stop` is fine
# here too - unlike the jobscheduler route, this does not need the job to still be registered.
cmd_run() {
	local serial="$1"
	adb -s "${serial}" shell am broadcast \
		-a "${RUN_ACTION}" \
		-p "${PACKAGE}" \
		--include-stopped-packages > /dev/null
	echo "  Worker enqueued. './send-notifications.sh logs' shows what it decided."
}

# WorkManager's own diagnostics: every enqueued request, its state, and its next scheduled run. This is the
# one that answers "is the reminder still armed with the app closed", and it needs no code of ours - the
# receiver ships inside WorkManager and requires the DUMP permission, which adb shell has.
#
# The broadcast only asks for the dump; the answer arrives in logcat a moment later, hence the sleep. The
# buffer is cleared first so the answer read back is this run's rather than the last one's - worth knowing
# if something else was being watched in logcat at the time.
cmd_status() {
	local serial="$1"
	adb -s "${serial}" logcat -c -b main 2>/dev/null || true
	adb -s "${serial}" shell am broadcast \
		-a "${DIAGNOSTICS_ACTION}" \
		-p "${PACKAGE}" \
		--include-stopped-packages > /dev/null
	sleep 2
	local dump
	dump="$(adb -s "${serial}" logcat -d -s WM-DiagnosticsWrkr | tr -d '\r' | tail -n +2)"
	if [[ -z "${dump}" ]]; then
		echo "  No diagnostics came back. The app may never have run since install; open it once."
	else
		echo "${dump}" | sed 's/^/  /'
	fi
}

cmd_jobs() {
	local serial="$1"
	local lines
	lines="$(job_lines "${serial}")"

	if [[ -z "${lines}" ]]; then
		wake_workmanager "${serial}"
		lines="$(job_lines "${serial}")"
	fi

	if [[ -z "${lines}" ]]; then
		echo "  No system job registered for ${PACKAGE}, even after waking WorkManager. That is not the same"
		echo "  as nothing being enqueued; './send-notifications.sh status' is the authority on that."
	else
		echo "${lines}" | sed 's/^/  /'
	fi
}

job_lines() {
	local serial="$1"
	adb -s "${serial}" shell dumpsys jobscheduler | tr -d '\r' \
		| grep -E -A 12 "^[[:space:]]*JOB .*${PACKAGE}/" || true
}

# Follows both sides: the debug receiver, and WorkManager's own record of a worker starting and finishing.
# Single device only, because interleaving two devices' logcat gives you no way to tell which said what.
cmd_logs() {
	local serial="$1"
	echo "  Following logs on ${serial}. Ctrl+C to stop."
	adb -s "${serial}" logcat -s DebugReminder WM-WorkerWrapper WM-DiagnosticsWrkr
}

SERIAL=""
COMMAND=""

while [[ $# -gt 0 ]]; do
	case "$1" in
		-s) SERIAL="${2:-}"; shift 2 ;;
		-h|--help|help) usage 0 ;;
		send|run|status|jobs|logs) COMMAND="$1"; shift ;;
		*) echo "Unknown argument: $1" >&2; usage 1 ;;
	esac
done

# Bare invocation keeps doing what this script did when it was one line.
COMMAND="${COMMAND:-send}"

# Into an array, and iterated with `for`, deliberately. Feeding the list into `while read` over a here-string
# looks equivalent and is not: `adb shell` inherits that here-string as its own stdin and swallows the
# remaining lines, so the loop silently stops after the first device. With two emulators connected that shows
# up as the second one never being mentioned at all.
mapfile -t TARGETS < <(resolve_targets)

# `logs` follows a stream and never returns, so it cannot be run against several devices in sequence.
if [[ "${COMMAND}" == "logs" && "${#TARGETS[@]}" -gt 1 ]]; then
	echo "Several devices are connected. Pick one with -s <serial>:" >&2
	printf '  %s\n' "${TARGETS[@]}" >&2
	exit 1
fi

for serial in "${TARGETS[@]}"; do
	echo "${serial}:"
	check_installed "${serial}" || continue
	case "${COMMAND}" in
		send) cmd_send "${serial}" ;;
		run) cmd_run "${serial}" || true ;;
		status) cmd_status "${serial}" ;;
		jobs) cmd_jobs "${serial}" ;;
		logs) cmd_logs "${serial}" ;;
	esac
done
