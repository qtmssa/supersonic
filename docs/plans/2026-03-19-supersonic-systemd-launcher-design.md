# Supersonic Systemd Launcher Design

## Goal

Provide a reusable local launcher that keeps `supersonic` alive after the CLI command returns in environments where shell-background processes may be reaped.

## Options Considered

1. Add a separate `assembly/bin/supersonic-systemd.sh` wrapper around `systemd --user`.
2. Merge `systemd` management into `assembly/bin/supersonic-daemon.sh`.
3. Document a manual `systemd-run --user` command without adding a script.

## Decision

Choose option 1.

- It keeps the existing `supersonic-daemon.sh` behavior intact for short-lived validation and compatibility.
- It gives a stable, reusable entrypoint for local durable runs.
- It matches the current repair conclusion that process supervision, not just application startup, is part of the runtime contract in this environment.

## Scope

- Add `start|stop|restart|status|logs` support.
- Reuse the same service selection, profile resolution, `.env` loading, runtime layout preparation, port selection, and JVM option resolution as the repaired launcher flow.
- Update internal run documentation to point local CLI users at the supervised path.
