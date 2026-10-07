#!/usr/bin/env python3
"""
smoke.py -- console-only smoke-test harness for the Gangland Warfare Paper server.

Drives ServerStartDebug.bat through stdin/stdout, deploys a chosen set of runtime
module jars, sends a scripted console command plan, watches the log for module
load lines / faults / errors, and writes a Markdown + JSON report per run.

Python 3 standard library only. See README.md in this directory for full usage.

IMPORTANT: this script intentionally touches ONLY files under this smoke/ directory
plus (optionally, with --deploy/--keystone/--restore) the target server's
plugins/ folder. It never builds the plugin and never touches git.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import zipfile
from pathlib import Path
from typing import Optional

THIS_DIR = Path(__file__).resolve().parent
DEFAULT_SCENARIOS = THIS_DIR / "scenarios.json"
DEFAULT_REPORTS_DIR = THIS_DIR / "reports"

DONE_PATTERN = re.compile(r"Done \(")
PRESS_ANY_KEY_PATTERN = re.compile(r"press any key", re.IGNORECASE)
LOADED_MODULE_PATTERN = re.compile(r"Loaded module\s+(\S+)")
LUCKYRAVEN_FRAME_PATTERN = re.compile(r"at org\.luckyraven\S*")

INTERESTING_SUBSTRINGS = [
    "ERROR", "WARN", "Exception", "Caused by", "[Keystone", "[Gangland",
    "Loaded module", "Runtime modules:", "module.", "Disabling", "enabled",
    "onDisabled",
]


# --------------------------------------------------------------------------- #
# Fake server (embedded, launched via --fake-server-run as its own subprocess)
# --------------------------------------------------------------------------- #

def fake_server_main(argv: list[str]) -> None:
    """
    Tiny stand-in for Paper, used only by --fake so the harness's process
    control / stdin-driving / log-parsing / reporting can be exercised without
    ever touching the real server. Prints Paper-shaped log lines to stdout AND
    mirrors them into logs/latest.log (relative to cwd) so the "copy
    logs/latest.log to reports/" step has something real to copy.

    argv: module ids to report as "loaded" (e.g. ["weapon", "gadget"]).
    """
    try:
        sys.stdin.reconfigure(encoding="utf-8", errors="replace")
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    module_ids = [a for a in argv if not a.startswith("--")]
    jar_prefix = {
        "mail": "gangland-mail",
        "cops": "cops-n-crooks",
        "gadget": "gangland-gadget",
        "turf": "gangland-turf",
        "weapon": "gangland-weapon",
    }

    logs_dir = Path("logs")
    logs_dir.mkdir(exist_ok=True)
    log_path = logs_dir / "latest.log"
    log_path.write_text("", encoding="utf-8")
    log_fh = open(log_path, "a", encoding="utf-8")

    def emit(line: str) -> None:
        sys.stdout.write(line + "\n")
        sys.stdout.flush()
        log_fh.write(line + "\n")
        log_fh.flush()

    emit("[00:00:00 INFO]: Starting minecraft server version 1.21.11 (FAKE)")
    emit("[00:00:00 INFO]: Loading properties")
    emit("[00:00:00 INFO]: [Keystone] Bootstrapping GanglandContext (FAKE)")
    for mid in module_ids:
        prefix = jar_prefix.get(mid, mid)
        emit(f"[00:00:01 INFO]: [Keystone Module.ModuleLoader] Loaded module {mid} 0.8.4 "
             f"from {prefix}-0.8.4.jar")
    emit(f"[00:00:01 INFO]: [Keystone Module.ModuleLoader] Runtime modules: "
         f"{len(module_ids)} loaded, 0 failed")
    emit("[00:00:02 WARN]: [FakeSmoke] simulated warning for harness self-test")
    emit("[00:00:02 ERROR]: [FakeSmoke] simulated error for harness self-test")
    emit("[00:00:02 ERROR]: [FakeSmoke] Caused by: java.lang.RuntimeException: fake smoke exception")
    emit("\tat org.luckyraven.gangland.fake.FakeService.doThing(FakeService.java:42)")
    emit('[00:00:05 INFO]: Done (5.123s)! For help, type "help"')

    try:
        for raw in sys.stdin:
            cmd = raw.rstrip("\r\n")
            if cmd == "":
                continue
            emit(f"> {cmd}")
            if cmd == "stop":
                emit("[00:00:10 INFO]: Stopping the server")
                emit("[00:00:10 INFO]: Saving players")
                emit("[00:00:10 INFO]: Saving worlds")
                emit("[00:00:11 INFO]: ChunkProviderServer: All dimensions are saved")
                emit("[00:00:11 INFO]: Disabling Gangland_Warfare vFAKE")
                for mid in module_ids:
                    emit(f"[00:00:11 INFO]: [Keystone Module.ModuleLoader] Disabling module {mid}")
                    emit(f"[00:00:11 INFO]: [Keystone Module.ModuleLoader] onDisabled {mid}")
                # NOTE: deliberately no inner "press any key" prompt here.
                # Real Paper's java process has no pause step of its own --
                # only the wrapping .bat's trailing `pause` does. Mirroring
                # that exactly keeps --fake mode topologically identical to
                # the real launch (one pause step total, handled by cmd.exe).
                break
            elif cmd.startswith("glw reload"):
                emit("[00:00:09 INFO]: Reloading Gangland Warfare (FAKE)")
                emit(f"[00:00:09 INFO]: [Keystone Module.ModuleLoader] Runtime modules: "
                     f"{len(module_ids)} loaded, 0 failed")
            elif cmd.startswith("glw"):
                emit(f"[00:00:03 INFO]: (fake) output for: {cmd}")
            else:
                emit('[00:00:03 INFO]: Unknown command. Type "/help" for help. (fake)')
    finally:
        log_fh.close()
    sys.exit(0)


# --------------------------------------------------------------------------- #
# Config loading
# --------------------------------------------------------------------------- #

def load_config(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def find_scenario(cfg: dict, sid: str) -> dict:
    for s in cfg["scenarios"]:
        if s["id"] == sid:
            return s
    raise KeyError(f"Unknown scenario id: {sid}")


def select_rows(cfg: dict, rows_arg: str) -> list[str]:
    wanted = [r.strip() for r in rows_arg.split(",") if r.strip()]
    known = {s["id"] for s in cfg["scenarios"]}
    unknown = [r for r in wanted if r not in known]
    if unknown:
        raise SystemExit(f"Unknown scenario id(s): {', '.join(unknown)}. "
                          f"Known ids: {', '.join(sorted(known))}")
    return wanted


# --------------------------------------------------------------------------- #
# Path / glob helpers
# --------------------------------------------------------------------------- #

def glob_one(base: Path, pattern: str) -> Optional[Path]:
    """
    Resolve `pattern` (a glob, may contain wildcards) against files directly
    under Path `base`. `base` itself is NEVER re-interpreted as a glob pattern
    (pathlib.Path.glob only parses the pattern argument), which matters here
    because the repo path contains literal '[' ']' characters
    ("Gangland Warfare [Cubed-GTA recoded]") that glob.glob() as a plain
    string WOULD mis-parse as a character class. Always call this helper
    (base Path + relative pattern) instead of glob.glob(full_path_string).
    Returns the newest match by mtime, or None.
    """
    if not base.exists():
        return None
    matches = sorted(base.glob(pattern), key=lambda p: p.stat().st_mtime, reverse=True)
    return matches[0] if matches else None


def glob_all(base: Path, pattern: str) -> list[Path]:
    if not base.exists():
        return []
    return sorted(base.glob(pattern))


# --------------------------------------------------------------------------- #
# Preflight checks
# --------------------------------------------------------------------------- #

def port_in_use(port: int) -> bool:
    """True if nothing can bind this port right now (i.e. something already owns it)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        s.bind(("0.0.0.0", port))
        return False
    except OSError:
        return True
    finally:
        s.close()


def find_conflicting_java_process() -> Optional[str]:
    """
    Look for a running java.exe whose command line mentions paper-1.21.11.jar.
    Tries wmic first, falls back to PowerShell Get-CimInstance, falls back to
    a bare tasklist presence check (which cannot see the command line, so it
    is reported as an unconfirmed possible conflict).
    Returns a human-readable description of the conflict, or None if clear.
    """
    needle = "paper-1.21.11.jar"

    # 1) wmic (may be absent on newer Windows builds)
    try:
        out = subprocess.run(
            ["wmic", "process", "where", "name='java.exe'", "get", "processid,commandline"],
            capture_output=True, text=True, timeout=15,
        )
        if out.returncode == 0 and out.stdout:
            for line in out.stdout.splitlines():
                if needle.lower() in line.lower():
                    return f"java.exe running with {needle} in its command line (via wmic): {line.strip()}"
            return None
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError):
        pass

    # 2) PowerShell Get-CimInstance (works when wmic is gone)
    try:
        ps_script = (
            "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" "
            "| Select-Object ProcessId,CommandLine | Format-List"
        )
        out = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps_script],
            capture_output=True, text=True, timeout=20,
        )
        if out.returncode == 0:
            if needle.lower() in out.stdout.lower():
                return f"java.exe running with {needle} in its command line (via PowerShell): see tasklist for PID"
            if "ProcessId" in out.stdout or out.stdout.strip() == "":
                # PowerShell ran fine and simply found nothing (or no java.exe at all).
                return None
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError):
        pass

    # 3) tasklist fallback -- no command line visibility, best-effort only.
    try:
        out = subprocess.run(
            ["tasklist", "/FI", "IMAGENAME eq java.exe"],
            capture_output=True, text=True, timeout=15,
        )
        if "java.exe" in out.stdout:
            return ("java.exe process(es) are running but this fallback (tasklist) cannot "
                    "read command lines to confirm it is paper-1.21.11.jar -- treating as a "
                    "possible conflict; verify manually with Task Manager -> Details -> "
                    "Command line column")
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError):
        pass

    return None


def preflight_checks(cfg: dict, args: argparse.Namespace) -> None:
    if args.fake:
        # Fake mode never touches ports 5005/25565 or spawns a real java.exe,
        # so real-server preflight checks are skipped by design.
        return

    problems = []
    if port_in_use(5005):
        problems.append("TCP port 5005 (JDWP debug) is already in use -- the real "
                         "ServerStartDebug.bat cannot bind address=*:5005 twice.")
    if port_in_use(25565):
        problems.append("TCP port 25565 (Minecraft) is already in use.")

    conflict = find_conflicting_java_process()
    if conflict:
        problems.append(f"Conflicting java.exe process detected: {conflict}")

    if problems:
        print("Preflight FAILED -- refusing to start the server:", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        sys.exit(3)


# --------------------------------------------------------------------------- #
# Module jar helpers (Host_Api rewrite for S7)
# --------------------------------------------------------------------------- #

def make_hostapi_variant(source_jar: Path, dest_jar: Path, new_host_api: str,
                         new_id: Optional[str] = None) -> None:
    """
    Copy source_jar to dest_jar with module.yml's `Host_Api:` value rewritten
    to new_host_api (and, when new_id is given, its `Id:` value rewritten too).
    Pure zipfile -- no shell, no external tools.

    Why new_id matters: Keystone's ModuleResolution.resolve() applies its rules in a
    fixed order -- 1. module.duplicate, 2. module.host.incompatible, 3.
    module.dependency.missing, 4. module.cycle. A Host_Api variant that keeps the
    source jar's `Id:` therefore collides with the original on rule 1 and is dropped
    as a duplicate (equal versions -> `candidateWins` is false -> the later-discovered
    jar loses) BEFORE the host-api check on rule 2 ever sees it, so the row would
    assert a fault that can never be emitted. Renaming the id lets both descriptors
    survive rule 1 so rule 2 is the one that rejects the variant.
    """
    with zipfile.ZipFile(source_jar, "r") as zin:
        if "module.yml" not in zin.namelist():
            raise RuntimeError(f"{source_jar} has no module.yml at its root")
        module_yml = zin.read("module.yml").decode("utf-8")

        new_lines = []
        replaced = False
        id_replaced = False
        for line in module_yml.splitlines(keepends=True):
            m = re.match(r"^(\s*Host_Api\s*:\s*)(\S+)(\s*\r?\n?)$", line)
            if m:
                new_lines.append(f"{m.group(1)}{new_host_api}{m.group(3)}")
                replaced = True
                continue
            if new_id is not None:
                mi = re.match(r"^(\s*Id\s*:\s*)(\S+)(\s*\r?\n?)$", line)
                if mi:
                    new_lines.append(f"{mi.group(1)}{new_id}{mi.group(3)}")
                    id_replaced = True
                    continue
            new_lines.append(line)
        if not replaced:
            raise RuntimeError(f"module.yml in {source_jar} has no Host_Api key to rewrite")
        if new_id is not None and not id_replaced:
            raise RuntimeError(f"module.yml in {source_jar} has no Id key to rewrite")
        new_yml_bytes = "".join(new_lines).encode("utf-8")

        dest_jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(dest_jar, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = new_yml_bytes if item.filename == "module.yml" else zin.read(item.filename)
                zout.writestr(item, data)


# --------------------------------------------------------------------------- #
# Deploy planning / execution
# --------------------------------------------------------------------------- #

def resolve_paths(cfg: dict) -> dict:
    repo_dir = Path(cfg["paths"]["repo_dir"])
    server_dir = Path(cfg["paths"]["server_dir"])
    plugins_dir = server_dir / cfg["deploy"]["server_plugins_dir"]
    modules_dir = plugins_dir / "Gangland_Warfare" / "modules"
    return {
        "repo_dir": repo_dir,
        "server_dir": server_dir,
        "plugins_dir": plugins_dir,
        "modules_dir": modules_dir,
        "modules_src_dir": repo_dir / cfg["deploy"]["modules_dir"],
        "core_jar_src_base": repo_dir,
        "keystone_src_dir": Path(cfg["paths"]["keystone_jar_dir"]),
        # T-O9: Bartizan is a sibling-repo build artifact, staged/removed per row like Keystone --
        # never built by this harness, only copied in (or parked out) by a fixed absolute path.
        "bartizan_jar_src": Path(cfg["paths"]["bartizan_jar"]) if "bartizan_jar" in cfg["paths"] else None,
    }


# --------------------------------------------------------------------------- #
# Plugin (plugins/ root) staging -- Bartizan, Citizens, the Gangland core jar
# --------------------------------------------------------------------------- #

# Plugins the D-row matrix toggles by name via a scenario's "plugins"/"remove_plugins" lists.
# "Gangland_Warfare" reuses the same core-jar glob the normal core_jar action already uses (D0:
# "no Gangland" removes it instead of the usual copy-in); Citizens is never built by this harness --
# only ever restored from whatever was already parked (a server owner's own install), never sourced
# fresh, so it can only be *removed* (D7), not staged into a server that never had it.
PLUGIN_NAME_GLOBS = {
    "Bartizan": "Bartizan-*.jar",
    "Citizens": "Citizens*.jar",
    "Gangland_Warfare": None,  # resolved from cfg["deploy"]["server_core_jar_glob_relative"] instead
}


def module_prefix(mid: str, cfg: dict) -> str:
    """
    Resolves a module id's jar-name prefix. Checks the live module_jar_prefix map first (the
    post-0.9.0 six-module topology D0-D9 use); falls back to legacy_module_jar_prefix for an id
    (e.g. "weapon") that map no longer carries, so the "legacy": true S1-S8 rows stay
    dry-run-verifiable against the pre-split topology without resurrecting "weapon" in the map the
    current D-rows read (T-O9).
    """
    if mid in cfg["module_jar_prefix"]:
        return cfg["module_jar_prefix"][mid]
    return cfg["legacy_module_jar_prefix"][mid]


def plugin_glob(name: str, cfg: dict) -> str:
    if name == "Gangland_Warfare":
        return cfg["deploy"]["server_core_jar_glob_relative"]
    if name not in PLUGIN_NAME_GLOBS:
        raise KeyError(f"Unknown plugin name '{name}' in a scenario's plugins/remove_plugins list "
                        f"-- known names: {sorted(PLUGIN_NAME_GLOBS)}")
    return PLUGIN_NAME_GLOBS[name]


def compute_deploy_plan(scenario: dict, cfg: dict, args: argparse.Namespace) -> dict:
    p = resolve_paths(cfg)
    actions = []
    remove_plugins = scenario.get("remove_plugins", [])

    if args.deploy:
        existing_core = glob_all(p["plugins_dir"], cfg["deploy"]["server_core_jar_glob_relative"])
        if "Gangland_Warfare" in remove_plugins:
            actions.append({
                "type": "core_jar",
                "src": None,
                "src_found": False,
                "removed_not_staged": True,
                "dest_dir": str(p["plugins_dir"]),
                "would_remove": [str(x) for x in existing_core],
            })
        else:
            core_src = glob_one(p["core_jar_src_base"], cfg["deploy"]["core_jar_glob"])
            actions.append({
                "type": "core_jar",
                "src": str(core_src) if core_src else None,
                "src_found": core_src is not None,
                "dest_dir": str(p["plugins_dir"]),
                "would_remove": [str(x) for x in existing_core],
            })

    if args.keystone:
        keystone_src = glob_one(p["keystone_src_dir"], cfg["paths"]["keystone_jar_pattern"])
        existing_keystone = glob_all(p["plugins_dir"], "Keystone-*.jar")
        actions.append({
            "type": "keystone_jar",
            "src": str(keystone_src) if keystone_src else None,
            "src_found": keystone_src is not None,
            "dest_dir": str(p["plugins_dir"]),
            "would_remove": [str(x) for x in existing_keystone],
        })

    if args.deploy:
        wanted = []
        for mid in scenario["modules"]:
            prefix = module_prefix(mid, cfg)
            src = glob_one(p["modules_src_dir"], f"{prefix}-*.jar")
            wanted.append({
                "module": mid,
                "src": str(src) if src else None,
                "src_found": src is not None,
                "dest": str(p["modules_dir"] / (src.name if src else f"{prefix}-NOT-FOUND.jar")),
            })

        for extra in scenario.get("extra_module_jars", []):
            src_module = extra["source_module"]
            prefix = module_prefix(src_module, cfg)
            src = glob_one(p["modules_src_dir"], f"{prefix}-*.jar")
            dest_name = f"{prefix}-{extra['name_suffix']}.jar"
            wanted.append({
                "module": f"{src_module} (Host_Api={extra['host_api_override']} variant)",
                "src": str(src) if src else None,
                "src_found": src is not None,
                "dest": str(p["modules_dir"] / dest_name),
                "host_api_override": extra["host_api_override"],
                "generated": True,
            })

        actions.append({
            "type": "modules_sync",
            "modules_dir": str(p["modules_dir"]),
            "parked_dir": str(p["modules_dir"] / ".harness-parked"),
            "existing_before_park": [x.name for x in glob_all(p["modules_dir"], "*.jar")],
            "wanted": wanted,
        })

        stage = []
        for name in scenario.get("plugins", []):
            if name == "Gangland_Warfare":
                continue
            if name == "Bartizan":
                src = p["bartizan_jar_src"]
                stage.append({"plugin": name, "src": str(src) if src else None,
                              "src_found": bool(src and src.exists())})
            else:
                stage.append({"plugin": name, "src": "(restored from .harness-parked-plugins, if any)",
                              "src_found": None})
        remove = [{"plugin": name, "matches": [x.name for x in glob_all(p["plugins_dir"], plugin_glob(name, cfg))]}
                  for name in remove_plugins if name != "Gangland_Warfare"]
        if stage or remove:
            actions.append({
                "type": "plugins_sync",
                "plugins_dir": str(p["plugins_dir"]),
                "parked_dir": str(p["plugins_dir"] / ".harness-parked-plugins"),
                "stage": stage,
                "remove": remove,
            })

    return {"scenario": scenario["id"], "actions": actions}


def print_plan(sid: str, plan: dict, command_plan: list[str]) -> None:
    print(f"\n--- Dry-run plan for {sid} ---")
    if not plan["actions"]:
        print("  (no --deploy/--keystone given: nothing would be copied)")
    for action in plan["actions"]:
        if action["type"] == "core_jar":
            if action.get("removed_not_staged"):
                print(f"  [core_jar] REMOVE ONLY (remove_plugins: Gangland_Warfare) -- Gangland core jar "
                      f"stays absent for this row")
                for rm in action["would_remove"]:
                    print(f"             would delete existing: {rm}")
            else:
                status = "FOUND" if action["src_found"] else "NOT FOUND"
                print(f"  [core_jar] would copy: {action['src']} ({status})")
                print(f"             -> {action['dest_dir']}")
                for rm in action["would_remove"]:
                    print(f"             would first delete existing: {rm}")
        elif action["type"] == "keystone_jar":
            status = "FOUND" if action["src_found"] else "NOT FOUND"
            print(f"  [keystone_jar] would copy: {action['src']} ({status})")
            print(f"                 -> {action['dest_dir']}")
            for rm in action["would_remove"]:
                print(f"                 would first delete existing: {rm}")
        elif action["type"] == "modules_sync":
            print(f"  [modules_sync] target dir: {action['modules_dir']}")
            print(f"                 existing jars there now (would be parked/cleared once): "
                  f"{action['existing_before_park'] or '(none)'}")
            print(f"                 parked-jar holding dir: {action['parked_dir']}")
            for w in action["wanted"]:
                status = "FOUND" if action.get("src_found", w["src_found"]) else "NOT FOUND"
                status = "FOUND" if w["src_found"] else "NOT FOUND"
                extra = " [GENERATED host_api variant]" if w.get("generated") else ""
                print(f"                 would place: {w['module']} <- {w['src']} ({status}){extra}")
                print(f"                              -> {w['dest']}")
        elif action["type"] == "plugins_sync":
            print(f"  [plugins_sync] target dir: {action['plugins_dir']}")
            print(f"                 parked-jar holding dir: {action['parked_dir']}")
            for s in action["stage"]:
                status = "FOUND" if s["src_found"] else ("NOT FOUND" if s["src_found"] is False else "?")
                print(f"                 would stage plugin: {s['plugin']} <- {s['src']} ({status})")
            for r in action["remove"]:
                print(f"                 would REMOVE plugin: {r['plugin']} "
                      f"(current matches: {r['matches'] or '(none present)'})")
    print(f"  Command plan ({len(command_plan)} commands): {command_plan}")


def ensure_parked(modules_dir: Path) -> None:
    """
    One-time, whole-matrix move-aside of whatever jars the user already had in
    plugins/Gangland_Warfare/modules/ before the harness starts overwriting
    that folder per-scenario. Safe to call once per smoke.py invocation; a
    marker file prevents re-parking the harness's own jars on later rows.
    """
    modules_dir.mkdir(parents=True, exist_ok=True)
    parked_dir = modules_dir / ".harness-parked"
    marker = parked_dir / ".parked-marker.json"
    if marker.exists():
        return  # already parked earlier in this invocation

    parked_dir.mkdir(parents=True, exist_ok=True)
    moved = []
    for f in list(modules_dir.iterdir()):
        if f == parked_dir:
            continue
        if f.is_file() and f.suffix.lower() == ".jar":
            dest = parked_dir / f.name
            shutil.move(str(f), str(dest))
            moved.append(f.name)
    marker.write_text(json.dumps({
        "parked_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "files": moved,
    }, indent=2), encoding="utf-8")
    print(f"[deploy] Parked {len(moved)} pre-existing module jar(s) into {parked_dir}: {moved}")


def restore_parked_modules(modules_dir: Path) -> None:
    parked_dir = modules_dir / ".harness-parked"
    marker = parked_dir / ".parked-marker.json"
    if not parked_dir.exists():
        print("[restore] No .harness-parked directory found -- nothing to restore.")
        return

    # Clear whatever the harness left behind from the last scenario run.
    for f in list(modules_dir.iterdir()):
        if f == parked_dir:
            continue
        if f.is_file() and f.suffix.lower() == ".jar":
            f.unlink()

    restored = []
    for f in list(parked_dir.iterdir()):
        if f.name == ".parked-marker.json":
            continue
        dest = modules_dir / f.name
        shutil.move(str(f), str(dest))
        restored.append(f.name)

    if marker.exists():
        marker.unlink()
    try:
        parked_dir.rmdir()
    except OSError:
        pass  # not empty for some reason -- leave it, don't lose data

    print(f"[restore] Restored {len(restored)} jar(s) to {modules_dir}: {restored}")


def ensure_parked_plugins(plugins_dir: Path, cfg: dict) -> None:
    """
    One-time, whole-matrix move-aside of any currently-installed jar matching a plugin name a D-row
    might stage or remove (Bartizan, Citizens -- never the Gangland core jar, which the existing
    core_jar action already owns and re-copies every row). Mirrors ensure_parked()'s module-dir
    behaviour, scoped to plugins/ root, into plugins/.harness-parked-plugins/. Only Bartizan/Citizens
    are ever parked here -- an unrelated plugins/ jar (Vault, NBTAPI, ...) is left alone.
    """
    plugins_dir.mkdir(parents=True, exist_ok=True)
    parked_dir = plugins_dir / ".harness-parked-plugins"
    marker = parked_dir / ".parked-marker.json"
    if marker.exists():
        return  # already parked earlier in this invocation

    parked_dir.mkdir(parents=True, exist_ok=True)
    moved = []
    for name in ("Bartizan", "Citizens"):
        for f in glob_all(plugins_dir, plugin_glob(name, cfg)):
            dest = parked_dir / f.name
            shutil.move(str(f), str(dest))
            moved.append(f.name)
    marker.write_text(json.dumps({
        "parked_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "files": moved,
    }, indent=2), encoding="utf-8")
    print(f"[deploy] Parked {len(moved)} pre-existing plugin jar(s) into {parked_dir}: {moved}")


def restore_parked_plugins(plugins_dir: Path) -> None:
    parked_dir = plugins_dir / ".harness-parked-plugins"
    marker = parked_dir / ".parked-marker.json"
    if not parked_dir.exists():
        print("[restore] No .harness-parked-plugins directory found -- nothing to restore.")
        return

    restored = []
    for f in list(parked_dir.iterdir()):
        if f.name == ".parked-marker.json":
            continue
        dest = plugins_dir / f.name
        if dest.exists():
            dest.unlink()  # a harness-staged copy (e.g. Bartizan) sits there from the last row
        shutil.move(str(f), str(dest))
        restored.append(f.name)

    if marker.exists():
        marker.unlink()
    try:
        parked_dir.rmdir()
    except OSError:
        pass  # not empty for some reason -- leave it, don't lose data

    print(f"[restore] Restored {len(restored)} plugin jar(s) to {plugins_dir}: {restored}")


def deploy_scenario(scenario: dict, cfg: dict, args: argparse.Namespace) -> dict:
    """Executes (not just plans) the deploy step. Returns a summary dict for the report."""
    p = resolve_paths(cfg)
    summary: dict = {"core_jar": None, "keystone_jar": None, "modules": [], "warnings": []}

    remove_plugins = scenario.get("remove_plugins", [])

    if args.deploy:
        p["plugins_dir"].mkdir(parents=True, exist_ok=True)
        if "Gangland_Warfare" in remove_plugins:
            # D0: "no Gangland" -- ensure the core jar is ABSENT rather than the usual copy-in.
            for old in glob_all(p["plugins_dir"], cfg["deploy"]["server_core_jar_glob_relative"]):
                old.unlink()
            summary["core_jar"] = None
        else:
            core_src = glob_one(p["core_jar_src_base"], cfg["deploy"]["core_jar_glob"])
            if core_src is None:
                summary["warnings"].append("No core jar found matching "
                                            f"{cfg['deploy']['core_jar_glob']} under {p['core_jar_src_base']}")
            else:
                for old in glob_all(p["plugins_dir"], cfg["deploy"]["server_core_jar_glob_relative"]):
                    old.unlink()
                dest = p["plugins_dir"] / core_src.name
                shutil.copy2(core_src, dest)
                summary["core_jar"] = str(dest)

    if args.keystone:
        keystone_src = glob_one(p["keystone_src_dir"], cfg["paths"]["keystone_jar_pattern"])
        if keystone_src is None:
            summary["warnings"].append("No Keystone jar found matching "
                                        f"{cfg['paths']['keystone_jar_pattern']} under {p['keystone_src_dir']}")
        else:
            p["plugins_dir"].mkdir(parents=True, exist_ok=True)
            for old in glob_all(p["plugins_dir"], "Keystone-*.jar"):
                old.unlink()
            dest = p["plugins_dir"] / keystone_src.name
            shutil.copy2(keystone_src, dest)
            summary["keystone_jar"] = str(dest)

    if args.deploy:
        ensure_parked(p["modules_dir"])
        # Clear whatever the previous scenario in this matrix left behind
        # (never touches .harness-parked/, which holds the user's own jars).
        for f in list(p["modules_dir"].iterdir()):
            if f.name == ".harness-parked":
                continue
            if f.is_file() and f.suffix.lower() == ".jar":
                f.unlink()

        for mid in scenario["modules"]:
            prefix = module_prefix(mid, cfg)
            src = glob_one(p["modules_src_dir"], f"{prefix}-*.jar")
            if src is None:
                summary["warnings"].append(f"No module jar found for '{mid}' "
                                            f"matching {prefix}-*.jar under {p['modules_src_dir']}")
                continue
            dest = p["modules_dir"] / src.name
            shutil.copy2(src, dest)
            summary["modules"].append(str(dest))

        for extra in scenario.get("extra_module_jars", []):
            src_module = extra["source_module"]
            prefix = module_prefix(src_module, cfg)
            src = glob_one(p["modules_src_dir"], f"{prefix}-*.jar")
            if src is None:
                summary["warnings"].append(f"No source module jar found for host_api variant "
                                            f"of '{src_module}'")
                continue
            dest = p["modules_dir"] / f"{prefix}-{extra['name_suffix']}.jar"
            make_hostapi_variant(src, dest, extra["host_api_override"], extra.get("id_override"))
            summary["modules"].append(str(dest))

    if args.deploy:
        # T-O9: stage/remove the extra plugins a D-row needs (Bartizan, Citizens) without hand-editing
        # the server between rows. Bartizan/Citizens jars already sitting in plugins/ are parked once
        # per invocation (ensure_parked_plugins) so each row starts from a known-absent baseline, then
        # restaged per this row's "plugins"/"remove_plugins" lists. "Gangland_Warfare" is handled by
        # the core_jar action above, not here.
        ensure_parked_plugins(p["plugins_dir"], cfg)
        parked_dir = p["plugins_dir"] / ".harness-parked-plugins"

        for name in scenario.get("plugins", []):
            if name == "Gangland_Warfare":
                continue
            if name == "Bartizan":
                if p["bartizan_jar_src"] is None or not p["bartizan_jar_src"].exists():
                    summary["warnings"].append(f"No Bartizan jar found at {p['bartizan_jar_src']} "
                                                f"(set paths.bartizan_jar in scenarios.json)")
                    continue
                dest = p["plugins_dir"] / p["bartizan_jar_src"].name
                shutil.copy2(p["bartizan_jar_src"], dest)
                summary["modules"].append(str(dest))
            else:
                # Every other pluggable name (Citizens) is never built by this harness -- it can
                # only be restored from what a real park found already installed on the server.
                parked = glob_all(parked_dir, plugin_glob(name, cfg)) if parked_dir.exists() else []
                if not parked:
                    summary["warnings"].append(f"'{name}' requested in \"plugins\" but no parked "
                                                f"{plugin_glob(name, cfg)} jar was found -- this harness "
                                                f"does not build {name} itself, only stages/restores it.")
                    continue
                for f in parked:
                    dest = p["plugins_dir"] / f.name
                    shutil.copy2(f, dest)
                    summary["modules"].append(str(dest))

        for name in remove_plugins:
            if name == "Gangland_Warfare":
                continue  # handled by the core_jar action above
            for f in glob_all(p["plugins_dir"], plugin_glob(name, cfg)):
                f.unlink()

    return summary


# --------------------------------------------------------------------------- #
# Command plan
# --------------------------------------------------------------------------- #

def build_command_plan(scenario: dict, cfg: dict) -> list[str]:
    if scenario.get("commands"):
        return list(scenario["commands"])

    plan = list(cfg["default_commands"])
    for mid in scenario["modules"]:
        probe = cfg["module_probe_commands"].get(mid)
        if probe and probe not in plan:
            plan.append(probe)
    return plan


# --------------------------------------------------------------------------- #
# Process driving
# --------------------------------------------------------------------------- #

class ServerProcess:
    """Wraps the cmd /c ServerStartDebug.bat process, its stdin, and a background
    reader thread that appends (timestamp, decoded_line) tuples to self.lines."""

    def __init__(self, argv: list[str], cwd: Path):
        self.argv = argv
        self.cwd = cwd
        self.lines: list[tuple[float, str]] = []
        self.proc: Optional[subprocess.Popen] = None
        self._reader: Optional[threading.Thread] = None

    def start(self) -> None:
        self.proc = subprocess.Popen(
            self.argv,
            cwd=str(self.cwd),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=False,
        )
        self._reader = threading.Thread(target=self._read_loop, daemon=True)
        self._reader.start()

    def _read_loop(self) -> None:
        assert self.proc is not None and self.proc.stdout is not None
        for raw_line in iter(self.proc.stdout.readline, b""):
            try:
                line = raw_line.decode("utf-8", errors="replace").rstrip("\r\n")
            except Exception:
                line = repr(raw_line)
            self.lines.append((time.time(), line))

    def send(self, command: str) -> None:
        assert self.proc is not None and self.proc.stdin is not None
        try:
            self.proc.stdin.write((command + "\n").encode("utf-8"))
            self.proc.stdin.flush()
        except (BrokenPipeError, OSError):
            pass

    def wait_for_pattern(self, pattern: re.Pattern, timeout: float,
                          start_index: int = 0) -> tuple[bool, int]:
        deadline = time.time() + timeout
        idx = start_index
        while time.time() < deadline:
            while idx < len(self.lines):
                _, line = self.lines[idx]
                idx += 1
                if pattern.search(line):
                    return True, idx
            if self.proc is not None and self.proc.poll() is not None:
                return False, idx  # process already gone -- pattern will never arrive
            time.sleep(0.2)
        return False, idx

    def wait_for_quiet_or_pattern(self, pattern: re.Pattern, quiet_seconds: float,
                                   timeout: float, start_index: int = 0) -> tuple[str, int]:
        """
        Returns as soon as EITHER `pattern` matches a complete line, OR no new
        line has been appended for `quiet_seconds`, OR the whole process tree
        already exited -- whichever comes first, bounded by `timeout`.

        This exists because Windows' `pause` (and this harness's own --fake
        stand-in) writes "Press any key to continue . . . " WITHOUT a
        trailing newline. readline() blocks on an unterminated line until
        either more bytes with a newline arrive or the pipe hits EOF, so a
        prompt like that can never be observed as a complete `pattern` match
        on its own -- waiting on the pattern alone would hang forever. The
        quiescence check is what actually fires in that case: once java has
        printed its last shutdown line and cmd is sitting at the unterminated
        prompt, self.lines simply stops growing.
        Returns (reason, idx) where reason is one of:
          "pattern" | "quiet" | "process_gone" | "timeout"
        """
        deadline = time.time() + timeout
        idx = start_index
        last_len = len(self.lines)
        last_change = time.time()
        while time.time() < deadline:
            cur_len = len(self.lines)
            if cur_len != last_len:
                for _, line in self.lines[idx:cur_len]:
                    if pattern.search(line):
                        return "pattern", cur_len
                idx = cur_len
                last_len = cur_len
                last_change = time.time()
            if time.time() - last_change >= quiet_seconds:
                return "quiet", idx
            if self.proc is not None and self.proc.poll() is not None:
                return "process_gone", idx
            time.sleep(0.3)
        return "timeout", idx

    def kill_tree(self) -> None:
        if self.proc is None:
            return
        try:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(self.proc.pid)],
                            capture_output=True, text=True, timeout=20)
        except Exception as e:
            print(f"[warn] taskkill failed for pid {self.proc.pid}: {e}", file=sys.stderr)


def perform_stop(server: ServerProcess, stop_timeout: float) -> tuple[bool, str]:
    """
    Sends 'stop', waits for the java process to finish (via output
    quiescence -- see wait_for_quiet_or_pattern's docstring for why pattern-
    matching the 'Press any key' prompt alone cannot work), releases the
    trailing `pause` with a newline, then waits for cmd.exe itself to exit.
    Bounded end to end by stop_timeout with one retry send -- never blocks
    indefinitely; on timeout the caller kills the whole process tree.
    Returns (clean_exit: bool, reason: str).
    """
    assert server.proc is not None
    start_idx = len(server.lines)
    server.send("stop")

    wait_budget = max(5.0, stop_timeout * 0.6)
    reason, _ = server.wait_for_quiet_or_pattern(
        PRESS_ANY_KEY_PATTERN, quiet_seconds=3.0, timeout=wait_budget, start_index=start_idx)

    # Release the trailing `pause`. Harmless in every case: if java is still
    # finishing up, Bukkit/Spigot consoles no-op on an empty command; if
    # `pause` hasn't started reading yet, the newline just sits buffered in
    # the pipe until pause consumes it the instant it starts.
    server.send("")

    first_wait = max(5.0, stop_timeout * 0.25)
    try:
        server.proc.wait(timeout=first_wait)
        return True, f"exited_after_{reason}"
    except subprocess.TimeoutExpired:
        pass

    # Retry once -- e.g. --fake mode has its own inner "stop" exit followed
    # by the .bat's *separate* real `pause`, so a second prompt can appear
    # after the first release; the real server only needs the one release
    # above, so this second send is a no-op there if cmd already exited.
    server.send("")
    second_wait = max(5.0, stop_timeout * 0.25)
    try:
        server.proc.wait(timeout=second_wait)
        return True, f"exited_after_retry_{reason}"
    except subprocess.TimeoutExpired:
        return False, f"timeout_waiting_for_cmd_exit(after_{reason})"


# --------------------------------------------------------------------------- #
# Fake server bootstrap
# --------------------------------------------------------------------------- #

class FakeServerContext:
    def __init__(self, root: Path):
        self.root = root
        self.bat_path = root / "ServerStartDebug.bat"

    def cleanup(self) -> None:
        try:
            shutil.rmtree(self.root, ignore_errors=True)
        except Exception:
            pass


def setup_fake_server_dir() -> FakeServerContext:
    tmp_root = Path(tempfile.mkdtemp(prefix="smoke_fake_server_"))
    (tmp_root / "logs").mkdir(parents=True, exist_ok=True)
    (tmp_root / "plugins" / "Gangland_Warfare" / "modules").mkdir(parents=True, exist_ok=True)

    python_exe = sys.executable
    script_path = str(Path(__file__).resolve())
    bat_content = (
        "@echo off\r\n"
        f'"{python_exe}" "{script_path}" --fake-server-run %*\r\n'
        "pause\r\n"
    )
    ctx = FakeServerContext(tmp_root)
    ctx.bat_path.write_text(bat_content, encoding="utf-8")
    return ctx


# --------------------------------------------------------------------------- #
# Log analysis
# --------------------------------------------------------------------------- #

def strip_timestamp(line: str) -> str:
    return re.sub(r"^(\[[^\]]*\]\s*)+", "", line).strip()


def extract_loaded_modules(all_text_lines: list[str]) -> list[str]:
    found = []
    for line in all_text_lines:
        m = LOADED_MODULE_PATTERN.search(line)
        if m:
            found.append(m.group(1))
    return found


def collect_interesting_lines(all_text_lines: list[str]) -> list[str]:
    return [l for l in all_text_lines if any(s in l for s in INTERESTING_SUBSTRINGS)]


def collect_error_signatures(all_text_lines: list[str]) -> dict[str, int]:
    sigs: dict[str, int] = {}
    for line in all_text_lines:
        if "ERROR" in line:
            sig = strip_timestamp(line)
            sigs[sig] = sigs.get(sig, 0) + 1
    return sigs


def collect_error_warn_with_frames(all_text_lines: list[str], limit: int = 40
                                    ) -> list[tuple[str, Optional[str]]]:
    result = []
    n = len(all_text_lines)
    for i, line in enumerate(all_text_lines):
        if "ERROR" in line or "WARN" in line:
            frame = None
            for j in range(i + 1, min(i + 15, n)):
                m = LUCKYRAVEN_FRAME_PATTERN.search(all_text_lines[j])
                if m:
                    frame = m.group(0)
                    break
            result.append((line, frame))
            if len(result) >= limit:
                break
    return result


def evaluate_expect(expect: dict, loaded_modules: list[str], all_text_lines: list[str],
                     error_lines: list[str]) -> tuple[bool, list[tuple[str, bool, str]]]:
    checks: list[tuple[str, bool, str]] = []
    overall_ok = True
    joined = "\n".join(all_text_lines)

    if "loaded_modules" in expect:
        want = sorted(expect["loaded_modules"])
        got = sorted(set(loaded_modules))
        passed = want == got
        overall_ok &= passed
        checks.append(("loaded_modules", passed, f"want={want} got={got}"))

    if "faults" in expect:
        missing = [f for f in expect["faults"] if f not in joined]
        passed = len(missing) == 0
        overall_ok &= passed
        detail = "all present" if passed else f"missing={missing}"
        checks.append(("faults", passed, detail))

    if "must_contain" in expect:
        missing = [s for s in expect["must_contain"] if s not in joined]
        passed = len(missing) == 0
        overall_ok &= passed
        detail = "all present" if passed else f"missing={missing}"
        checks.append(("must_contain", passed, detail))

    if "must_not_contain" in expect:
        # T-O9: negative-assertion counterpart to must_contain -- e.g. "Failed to create a backup"
        # (T-16 regression guard) or Bartizan's "Failed to import the legacy weapon table" /
        # "No suitable driver found" boot-log lines (REVIEW-bartizan-FINAL.md §4) must NEVER appear.
        present = [s for s in expect["must_not_contain"] if s in joined]
        passed = len(present) == 0
        overall_ok &= passed
        detail = "none present" if passed else f"forbidden text found={present}"
        checks.append(("must_not_contain", passed, detail))

    if "no_errors_except" in expect:
        allowed = [re.compile(p) for p in expect["no_errors_except"]]
        offending = [l for l in error_lines if not any(p.search(l) for p in allowed)]
        passed = len(offending) == 0
        overall_ok &= passed
        detail = "clean" if passed else f"offending (first 5)={offending[:5]}"
        checks.append(("no_errors_except", passed, detail))

    return overall_ok, checks


# --------------------------------------------------------------------------- #
# Reporting
# --------------------------------------------------------------------------- #

def write_scenario_report(reports_dir: Path, stamp: str, scenario: dict,
                           deploy_summary: dict, boot_ok: bool, command_plan: list[str],
                           transcripts: list[tuple[str, list[str]]], stop_ok: bool,
                           stop_reason: str, loaded_modules: list[str],
                           interesting_lines: list[str], error_signatures: dict[str, int],
                           error_warn_frames: list[tuple[str, Optional[str]]],
                           expect_ok: bool, expect_checks: list[tuple[str, bool, str]],
                           copied_log_path: Optional[Path], verdict: str) -> Path:
    report_path = reports_dir / f"{stamp}-{scenario['id']}.md"
    lines = []
    lines.append(f"# Smoke report: {scenario['id']} -- {scenario['title']}")
    lines.append("")
    lines.append(f"**Verdict: {verdict}**")
    lines.append("")
    lines.append(f"- Boot detected (`Done (`): {boot_ok}")
    lines.append(f"- Clean stop: {stop_ok} ({stop_reason})")
    lines.append(f"- Modules requested: {scenario['modules']}")
    lines.append(f"- Copied log: `{copied_log_path}`" if copied_log_path else "- Copied log: (none -- run did not reach a copyable log)")
    lines.append("")

    lines.append("## Deploy")
    lines.append("")
    if deploy_summary is None:
        lines.append("(no --deploy given -- server's existing jars were used as-is)")
    else:
        lines.append(f"- core_jar: `{deploy_summary.get('core_jar')}`")
        lines.append(f"- keystone_jar: `{deploy_summary.get('keystone_jar')}`")
        lines.append(f"- modules: {deploy_summary.get('modules')}")
        if deploy_summary.get("warnings"):
            lines.append("- warnings:")
            for w in deploy_summary["warnings"]:
                lines.append(f"  - {w}")
    lines.append("")

    lines.append("## Expectations")
    lines.append("")
    lines.append("| Check | Result | Detail |")
    lines.append("|---|---|---|")
    for name, passed, detail in expect_checks:
        lines.append(f"| {name} | {'PASS' if passed else 'FAIL'} | {detail} |")
    if not expect_checks:
        lines.append("| (none declared) | -- | -- |")
    lines.append("")

    lines.append("## Loaded module lines")
    lines.append("")
    lines.append(f"`Loaded module` ids seen: {loaded_modules}")
    lines.append("")

    lines.append("## Command transcripts")
    lines.append("")
    for cmd, output in transcripts:
        lines.append(f"### `{cmd}`")
        lines.append("```")
        lines.append(f">>> {cmd}")
        for l in output:
            lines.append(l)
        lines.append("```")
        lines.append("")

    lines.append(f"## Distinct ERROR signatures ({len(error_signatures)})")
    lines.append("")
    if error_signatures:
        for sig, count in sorted(error_signatures.items(), key=lambda kv: -kv[1]):
            lines.append(f"- ({count}x) `{sig}`")
    else:
        lines.append("(none)")
    lines.append("")

    lines.append(f"## First {len(error_warn_frames)} ERROR/WARN lines (with first "
                  f"org.luckyraven frame if any)")
    lines.append("")
    if error_warn_frames:
        for line, frame in error_warn_frames:
            lines.append(f"- `{line}`")
            if frame:
                lines.append(f"  - {frame}")
    else:
        lines.append("(none)")
    lines.append("")

    lines.append("## All interesting log lines")
    lines.append("")
    lines.append("<details><summary>expand</summary>")
    lines.append("")
    lines.append("```")
    for l in interesting_lines:
        lines.append(l)
    lines.append("```")
    lines.append("")
    lines.append("</details>")
    lines.append("")

    report_path.write_text("\n".join(lines), encoding="utf-8")
    return report_path


def write_summary(reports_dir: Path, stamp: str, rows: list[dict]) -> tuple[Path, Path]:
    md_path = reports_dir / f"{stamp}-summary.md"
    json_path = reports_dir / f"{stamp}-summary.json"

    md_lines = [f"# Smoke matrix summary -- {stamp}", "",
                "| Row | Title | Verdict | Boot | Stop | Loaded modules | Distinct ERRORs | Report |",
                "|---|---|---|---|---|---|---|---|"]
    for r in rows:
        md_lines.append(
            f"| {r['id']} | {r['title']} | {r['verdict']} | {r['boot_ok']} | "
            f"{r['stop_ok']} ({r['stop_reason']}) | {r['loaded_modules']} | "
            f"{r['distinct_error_count']} | `{Path(r['report_path']).name if r['report_path'] else ''}` |"
        )
    md_path.write_text("\n".join(md_lines), encoding="utf-8")
    json_path.write_text(json.dumps(rows, indent=2), encoding="utf-8")
    return md_path, json_path


def print_summary_table(rows: list[dict]) -> None:
    print("\n=== Summary ===")
    for r in rows:
        print(f"  {r['id']:4} {r['verdict']:5} boot={r['boot_ok']} stop={r['stop_ok']} "
              f"modules={r['loaded_modules']} errors={r['distinct_error_count']} "
              f"title={r['title']}")


def print_rows(cfg: dict) -> None:
    print(f"{'id':4} {'modules':30} title")
    for s in cfg["scenarios"]:
        # T-O9: S1-S8 target the pre-0.9.0 five-module topology and stay dry-run-verifiable, but no
        # longer represent the shipped six-module + Bartizan matrix -- labelled, not deleted.
        tag = " [legacy]" if s.get("legacy") else ""
        print(f"{s['id']:4} {str(s['modules']):30} {s['title']}{tag}")


# --------------------------------------------------------------------------- #
# Run one scenario end to end
# --------------------------------------------------------------------------- #

def run_scenario(scenario: dict, cfg: dict, args: argparse.Namespace, stamp: str,
                  reports_dir: Path, fake_ctx: Optional[FakeServerContext]) -> dict:
    p = resolve_paths(cfg)
    boot_timeout = cfg.get("boot_timeout_seconds", 180)
    stop_timeout = cfg.get("stop_timeout_seconds", 120)
    command_delay = cfg.get("command_delay_seconds", 2)

    command_plan = build_command_plan(scenario, cfg)

    deploy_summary = None
    if args.fake:
        server_dir = fake_ctx.root
        argv = ["cmd", "/c", str(fake_ctx.bat_path)] + list(scenario["modules"])
    else:
        server_dir = p["server_dir"]
        # NOTE: cmd /c with a *bare* relative filename ("ServerStartDebug.bat")
        # was found on this machine to fail non-interactively with "is not
        # recognized as an internal or external command" even though `cwd`
        # is set correctly and `cmd /c dir` in that same cwd shows the file --
        # reproducible on both C: and E: drives, unrelated to registry
        # NoDefaultCurrentDirectoryInExePath (checked, unset). Passing the
        # resolved absolute path to the .bat sidesteps that SearchPath quirk
        # while `cwd` still governs the batch's (and java's) actual working
        # directory -- verified equivalent in a standalone repro. See
        # README.md "Windows quirks" for the full writeup.
        argv = ["cmd", "/c", str(server_dir / "ServerStartDebug.bat")]
        if args.deploy or args.keystone:
            deploy_summary = deploy_scenario(scenario, cfg, args)

    server = ServerProcess(argv, server_dir)
    boot_ok = False
    stop_ok = False
    stop_reason = "not_attempted"
    transcripts: list[tuple[str, list[str]]] = []
    copied_log_path: Optional[Path] = None

    try:
        server.start()
        boot_ok, idx = server.wait_for_pattern(DONE_PATTERN, boot_timeout)

        if not boot_ok:
            print(f"[{scenario['id']}] BOOT TIMEOUT after {boot_timeout}s -- killing process tree.")
            server.kill_tree()
        else:
            for pre in scenario.get("pre_commands", []):
                time.sleep(pre.get("wait_before", 0))
                start_idx = len(server.lines)
                server.send(pre["command"])
                time.sleep(command_delay)
                transcripts.append((pre["command"], [l for _, l in server.lines[start_idx:]]))

            for cmd in command_plan:
                start_idx = len(server.lines)
                server.send(cmd)
                time.sleep(command_delay)
                transcripts.append((cmd, [l for _, l in server.lines[start_idx:]]))

            stop_ok, stop_reason = perform_stop(server, stop_timeout)
            if not stop_ok:
                print(f"[{scenario['id']}] STOP TIMEOUT -- killing process tree.")
                server.kill_tree()
    finally:
        # Best-effort: whatever happens, try to copy the log before the next
        # scenario's boot would gzip it away.
        latest_log = server_dir / "logs" / "latest.log"
        if latest_log.exists():
            copied_log_path = reports_dir / f"{stamp}-{scenario['id']}.log"
            try:
                shutil.copy2(latest_log, copied_log_path)
            except OSError as e:
                print(f"[{scenario['id']}] warn: could not copy latest.log: {e}", file=sys.stderr)
                copied_log_path = None

    all_text_lines = [l for _, l in server.lines]
    loaded_modules = extract_loaded_modules(all_text_lines)
    interesting_lines = collect_interesting_lines(all_text_lines)
    error_lines = [l for l in all_text_lines if "ERROR" in l]
    error_signatures = collect_error_signatures(all_text_lines)
    error_warn_frames = collect_error_warn_with_frames(all_text_lines)

    expect = scenario.get("expect", {})
    expect_ok, expect_checks = evaluate_expect(expect, loaded_modules, all_text_lines, error_lines)

    if not boot_ok:
        verdict = "FAIL"
    elif not stop_ok:
        verdict = "FAIL"
    elif not expect_ok:
        verdict = "FAIL"
    else:
        verdict = "PASS"

    report_path = write_scenario_report(
        reports_dir, stamp, scenario, deploy_summary, boot_ok, command_plan, transcripts,
        stop_ok, stop_reason, loaded_modules, interesting_lines, error_signatures,
        error_warn_frames, expect_ok, expect_checks, copied_log_path, verdict,
    )

    return {
        "id": scenario["id"],
        "title": scenario["title"],
        "verdict": verdict,
        "boot_ok": boot_ok,
        "stop_ok": stop_ok,
        "stop_reason": stop_reason,
        "loaded_modules": loaded_modules,
        "distinct_error_count": len(error_signatures),
        "report_path": str(report_path),
        "log_path": str(copied_log_path) if copied_log_path else None,
    }


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #

def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Console-only smoke-test harness for the Gangland Warfare Paper server.")
    parser.add_argument("--rows", type=str, default=None,
                         help="Comma-separated scenario ids to run, e.g. S1,S2")
    parser.add_argument("--deploy", action="store_true",
                         help="Copy the freshly built core jar + row's module jars into the server")
    parser.add_argument("--keystone", action="store_true",
                         help="Also copy the freshly built Keystone jar into the server")
    parser.add_argument("--restore", action="store_true",
                         help="At the end, move any parked pre-existing module jars back")
    parser.add_argument("--dry-run", action="store_true",
                         help="Print the deploy + command plan; never starts the server")
    parser.add_argument("--fake", action="store_true",
                         help="Drive an embedded fake server instead of the real Paper server")
    parser.add_argument("--list", action="store_true", help="List all scenario rows and exit")
    parser.add_argument("--scenarios", type=str, default=str(DEFAULT_SCENARIOS),
                         help="Path to scenarios.json")
    parser.add_argument("--reports-dir", type=str, default=str(DEFAULT_REPORTS_DIR),
                         help="Directory to write reports into")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    if len(argv) >= 1 and argv[0] == "--fake-server-run":
        fake_server_main(argv[1:])
        return 0

    args = parse_args(argv)
    cfg = load_config(Path(args.scenarios))

    if args.list:
        print_rows(cfg)
        return 0

    if not args.rows and not args.restore:
        print("Nothing to do: pass --rows S1,S2,... (or --list, or --restore).", file=sys.stderr)
        return 2

    reports_dir = Path(args.reports_dir)
    reports_dir.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y-%m-%d-%H%M")

    selected = select_rows(cfg, args.rows) if args.rows else []

    fake_ctx: Optional[FakeServerContext] = None
    if args.fake:
        fake_ctx = setup_fake_server_dir()
        print(f"[fake] Fake server dir: {fake_ctx.root}")

    if selected and not args.dry_run:
        preflight_checks(cfg, args)

    summary_rows: list[dict] = []
    try:
        for sid in selected:
            scenario = find_scenario(cfg, sid)
            print(f"\n=== {sid}: {scenario['title']} ===")

            if args.dry_run:
                plan = compute_deploy_plan(scenario, cfg, args)
                print_plan(sid, plan, build_command_plan(scenario, cfg))
                continue

            result = run_scenario(scenario, cfg, args, stamp, reports_dir, fake_ctx)
            summary_rows.append(result)
            print(f"[{sid}] verdict={result['verdict']} "
                  f"boot={result['boot_ok']} stop={result['stop_ok']} "
                  f"modules={result['loaded_modules']} errors={result['distinct_error_count']}")
    finally:
        if fake_ctx:
            fake_ctx.cleanup()

    if summary_rows:
        md_path, json_path = write_summary(reports_dir, stamp, summary_rows)
        print_summary_table(summary_rows)
        print(f"\nSummary written to:\n  {md_path}\n  {json_path}")

    if args.restore:
        if args.fake:
            print("[restore] --fake mode: nothing on the real server to restore.")
        else:
            p = resolve_paths(cfg)
            restore_parked_modules(p["modules_dir"])
            restore_parked_plugins(p["plugins_dir"])

    any_fail = any(r["verdict"] != "PASS" for r in summary_rows)
    return 1 if any_fail else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
