#!/usr/bin/env bash
# bench.sh — benchmark the cheap worker backends on the SAME coding task.
# Measures wall time, output size (token proxy), and code quality (file correctness).
# Usage: ./bench.sh            (all 9 cheap backends, sequential, in the background)
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
BENCH="$REPO/_bench"
mkdir -p "$BENCH"
. "$HERE/agents.conf"

# the 9 cheap backends (excludes claude-frontier = Anthropic)
BACKENDS=( kimi-flash kimi-pro command-flash command-pro opencode-bigpickle opencode-flash opencode-pro claude-flash claude-pro )
SUMMARY="$BENCH/SUMMARY.tsv"
echo -e "backend\tstatus\twall_s\tout_chars\tfib_correct\tquality\tnotes" > "$SUMMARY"

for b in "${BACKENDS[@]}"; do
  dir="$BENCH/$b"; mkdir -p "$dir"
  # task is identical for every backend (differs only in the output dir)
  task="BENCH TASK: in the directory $dir create a single file fib.py. It must define fib(n) iteratively, then print fib(20). Add assert fib(20)==6765. Then run it with 'python $dir/fib.py' (or python3) and confirm it prints 6765. Reply with exactly one line: FIB_OK=<the printed number>. Do not ask questions. If a tool fails, adapt and still deliver fib.py."
  start=$(date +%s.%N)
  out="$( (cd "$REPO" && AGENT_CLI="$b" agent_exec "$task" "" ) 2>&1 )"
  rc=$?
  end=$(date +%s.%N)
  wall=$(echo "$end $start" | awk '{printf "%.1f", $1-$2}')
  # output size = token proxy (chars/4)
  ochars=${#out}
  # correctness: run the produced fib.py
  if [ -f "$dir/fib.py" ]; then
    got=$( (cd "$dir" && python fib.py 2>&1) | tail -1 )
    if [ "$got" = "6765" ]; then corr="YES"; else corr="no($got)"; fi
    # crude quality: count lines + has def + has assert
    lines=$(wc -l < "$dir/fib.py")
    quality="lines=$lines"
  else
    corr="NO_FILE"; quality="?"
  fi
  echo -e "$b\t$rc\t$wall\t$ochars\t$corr\t$quality\t-"
  echo -e "$b\t$rc\t$wall\t$ochars\t$corr\t$quality\t-" >> "$SUMMARY"
  echo "$out" > "$BENCH/$b/run.out"
done
echo "=== DONE ==="; column -t -s $'\t' "$SUMMARY"
