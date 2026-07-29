#!/usr/bin/env python3
r"""
Decompile a DEX file with jadx and grep the output for portalCore / DES
domain-decryption logic.

Built to run the MOMENT the unidbg lever fully clears and `N.b2b()` writes a
real decrypted DEX (per NEXT-BLOCKER.md / HANDOFF.md session 15e). Until then
it can be dry-run against any structurally-real DEX (see --help for what was
used to validate it this session).

Usage:
    python scripts/analyze_decrypted_dex.py [DEX_PATH] [--out OUT_DIR] [--jadx JADX_BAT]

    DEX_PATH defaults to /tmp/apkx/app_decrypted.dex (the path the harness in
    _scratch/Unpack.java writes to on .40 once N.b2b succeeds -- see
    "Pull the real file" below). That default path won't exist locally on
    Win11 until it's copied over; pass an explicit path to test against
    something else in the meantime.

Pull the real file once it exists (paramiko pattern copied from
_scratch/run_lever_remote.py -- same host/user/password used throughout this
repo's _scratch/*.py scripts):

    python -c "
import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.100.40', username='nestor', password='ian20jesus',
          look_for_keys=False, allow_agent=False, timeout=15)
sftp = c.open_sftp()
sftp.get('/tmp/apkx/app_decrypted.dex', r'C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_session\app_decrypted.dex')
sftp.close(); c.close()
print('pulled')
"

Then run:
    python scripts/analyze_decrypted_dex.py C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_session\app_decrypted.dex

What it does:
    1. Shells out to jadx (jadx.bat on Windows) to decompile the DEX into a
       source tree (skipped if that tree already exists and --force isn't
       passed -- decompiling a large DEX can take minutes).
    2. Greps the decompiled sources for known search terms (portalCore /
       DES-domain-decryption related, from NEXT-BLOCKER.md's "DES domain key"
       section and prior TeleLatino analysis).
    3. Prints a structured summary: file:line hits grouped by search term.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Default jadx location on this Win11 box (HANDOFF.md "Quick reference").
DEFAULT_JADX_BAT = Path("C:/Users/Nestor/Downloads/jadx/bin/jadx.bat")
DEFAULT_JAVA_HOME = Path("C:/Program Files/Microsoft/jdk-17.0.18.8-hotspot")

# The path _scratch/Unpack.java writes to on .40 once N.b2b(ijiami.dat)
# succeeds (per the task background / HANDOFF.md). Not present on Win11
# until pulled via the SFTP snippet above.
DEFAULT_DEX_PATH = Path("/tmp/apkx/app_decrypted.dex")

# Search terms from NEXT-BLOCKER.md ("DES domain key" section) and prior
# TeleLatino analysis (session 14) -- portalCore API surface + DES/DESede
# domain-decryption logic. Kept as a plain list (not a set) so the printed
# summary preserves this order.
SEARCH_TERMS: list[str] = [
    "portalCore",
    "startPlayLive",
    "getLiveData",
    "domain_DES",
    "DESedeKeySpec",
    "SecretKeySpec",
    "IvParameterSpec",
    "getDomain",
    "setDomain",
    "domainKey",
    "portal_code",
]

# File extensions worth grepping in a jadx output tree.
SOURCE_GLOBS = ("*.java", "*.kt", "*.smali")


def decompile_with_jadx(dex_path: Path, out_dir: Path, jadx_bat: Path, force: bool) -> None:
    """Shell out to jadx to decompile dex_path into out_dir.

    Skips the (slow) decompile step if out_dir already has a sources/ tree,
    unless force is True.
    """
    sources_dir = out_dir / "sources"
    if sources_dir.exists() and any(sources_dir.iterdir()) and not force:
        print(f"[skip] {sources_dir} already populated (use --force to redo)")
        return

    if not jadx_bat.exists():
        raise FileNotFoundError(
            f"jadx not found at {jadx_bat} -- pass --jadx to point at jadx.bat "
            "(or the 'jadx' script on non-Windows), or install via "
            "'sudo snap install jadx' on .40 per HANDOFF.md."
        )
    if not dex_path.exists():
        raise FileNotFoundError(f"DEX not found: {dex_path}")

    out_dir.mkdir(parents=True, exist_ok=True)

    env = os.environ.copy()
    if DEFAULT_JAVA_HOME.exists():
        env["JAVA_HOME"] = str(DEFAULT_JAVA_HOME)

    cmd = [str(jadx_bat), "-d", str(out_dir), str(dex_path)]
    print(f"[jadx] {' '.join(cmd)}")
    result = subprocess.run(
        cmd,
        env=env,
        capture_output=True,
        text=True,
        timeout=1800,
    )
    # jadx routinely exits non-zero on partial-decompile warnings (obfuscated
    # / protected code) -- that's expected here, not a hard failure. Only
    # treat "no sources produced at all" as fatal.
    tail = "\n".join(result.stdout.splitlines()[-15:])
    print(f"[jadx] exit={result.returncode}\n{tail}")
    if not sources_dir.exists() or not any(sources_dir.rglob("*")):
        print("STDERR:", result.stderr[-3000:])
        raise RuntimeError(f"jadx produced no sources under {sources_dir}")


def iter_source_files(out_dir: Path):
    sources_dir = out_dir / "sources"
    search_root = sources_dir if sources_dir.exists() else out_dir
    for pattern in SOURCE_GLOBS:
        yield from search_root.rglob(pattern)


def grep_terms(out_dir: Path, terms: list[str]) -> dict[str, list[tuple[Path, int, str]]]:
    """Case-sensitive substring grep across decompiled sources, grouped by term."""
    hits: dict[str, list[tuple[Path, int, str]]] = {term: [] for term in terms}
    files = list(iter_source_files(out_dir))
    print(f"[grep] scanning {len(files)} source files for {len(terms)} terms")

    for path in files:
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for lineno, line in enumerate(text.splitlines(), start=1):
            for term in terms:
                if term in line:
                    hits[term].append((path, lineno, line.strip()))
    return hits


def print_summary(hits: dict[str, list[tuple[Path, int, str]]], out_dir: Path) -> None:
    total = sum(len(v) for v in hits.values())
    print("\n" + "=" * 72)
    print(f"SUMMARY -- {total} hits across {len(hits)} search terms")
    print("=" * 72)
    for term, matches in hits.items():
        print(f"\n--- {term} ({len(matches)} hits) ---")
        if not matches:
            continue
        for path, lineno, line in matches[:50]:
            try:
                rel = path.relative_to(out_dir)
            except ValueError:
                rel = path
            snippet = line if len(line) <= 160 else line[:157] + "..."
            print(f"  {rel}:{lineno}: {snippet}")
        if len(matches) > 50:
            print(f"  ... {len(matches) - 50} more (truncated)")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "dex_path",
        nargs="?",
        default=str(DEFAULT_DEX_PATH),
        help=f"Path to the DEX file to analyze (default: {DEFAULT_DEX_PATH})",
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Decompile output directory (default: <dex_stem>_jadx next to the DEX)",
    )
    parser.add_argument(
        "--jadx",
        default=str(DEFAULT_JADX_BAT),
        help=f"Path to jadx executable/bat (default: {DEFAULT_JADX_BAT})",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-run jadx decompile even if output already exists",
    )
    parser.add_argument(
        "--skip-decompile",
        action="store_true",
        help="Skip the jadx step entirely and just grep an existing --out tree",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    # Decompiled sources routinely contain non-ASCII bytes (obfuscated names,
    # stray UTF-8) that crash a raw cp1252 Windows console -- same gotcha
    # documented in HANDOFF.md for SSH output. Force UTF-8 stdout with
    # replacement instead of a hard crash mid-summary.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    args = parse_args(argv)
    dex_path = Path(args.dex_path)
    out_dir = Path(args.out) if args.out else dex_path.with_name(dex_path.stem + "_jadx")
    jadx_bat = Path(args.jadx)

    if args.skip_decompile:
        print(f"[skip-decompile] grepping existing tree: {out_dir}")
    else:
        decompile_with_jadx(dex_path, out_dir, jadx_bat, args.force)

    hits = grep_terms(out_dir, SEARCH_TERMS)
    print_summary(hits, out_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
