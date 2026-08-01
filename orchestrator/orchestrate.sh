#!/usr/bin/env bash
# orchestrate.sh — dispatch headless coding-CLI worker agents into isolated git
# worktrees/branches, poll them, and collect their work as PRs. CLI-agnostic
# (kimi by default; see agents.conf). The ORCHESTRATOR (an expensive Claude
# session) drives this; WORKERS are cheaper CLI agents in their own terminals/
# processes. Coordination is filesystem + git — never screen-watching.
#
# Usage:
#   ./orchestrate.sh dispatch <task> <branch> [model]   # spawn a worker on a new branch
#   ./orchestrate.sh status                              # show all runs (alive?, commits, tail)
#   ./orchestrate.sh logs <task> [n]                     # tail a worker's log
#   ./orchestrate.sh collect <task>                      # show branch diff + open a PR (needs gh)
#   ./orchestrate.sh stop <task> [--rm-worktree]         # kill a worker (optionally drop its worktree)
#   ./orchestrate.sh list                                # list task specs + registry
#
# A task spec is orchestrator/tasks/<task>.md — a self-contained prompt for the worker.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
TASKS="$HERE/tasks"
RUNS="$HERE/runs"
REG="$RUNS/registry.tsv"          # pid<TAB>branch<TAB>task<TAB>started<TAB>worktree
mkdir -p "$TASKS" "$RUNS"
[ -f "$REG" ] || : > "$REG"
# shellcheck disable=SC1091
. "$HERE/agents.conf"

die(){ echo "orchestrate: $*" >&2; exit 1; }
have(){ command -v "$1" >/dev/null 2>&1; }

running_count(){ # count still-alive registry pids
  local pid _ n=0
  while IFS=$'\t' read -r pid _; do [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && n=$((n+1)); done < "$REG"
  echo "$n"
}

cmd_dispatch(){
  local task="${1:-}" branch="${2:-}" model="${3:-$MODEL_WORKER}"
  [ -n "$task" ] && [ -n "$branch" ] || die "usage: dispatch <task> <branch> [model]"
  local tf="$TASKS/$task.md"
  [ -f "$tf" ] || die "no task spec: $tf"
  { [ "$AGENT_CLI" = "claude-deepseek" ] && have claude; } || have "$AGENT_CLI" || die "worker CLI '$AGENT_CLI' not on PATH"
  [ "$(running_count)" -lt "$MAX_PARALLEL" ] || die "MAX_PARALLEL=$MAX_PARALLEL reached; wait or raise it"

  local wtbase; wtbase="$(cd "$REPO" && mkdir -p "$WT_BASE" 2>/dev/null; cd "$REPO/$WT_BASE" 2>/dev/null && pwd)"
  [ -n "$wtbase" ] || die "cannot resolve WT_BASE=$WT_BASE under $REPO"
  local wt="$wtbase/$branch"
  if git -C "$REPO" show-ref --verify --quiet "refs/heads/$branch"; then
    echo "branch $branch exists — reusing worktree $wt"
    [ -d "$wt" ] || git -C "$REPO" worktree add "$wt" "$branch" || die "worktree add failed"
  else
    git -C "$REPO" worktree add -b "$branch" "$wt" master || die "worktree add -b failed"
  fi

  local log="$RUNS/$task.log" prompt; prompt="$(cat "$tf")"
  echo "== dispatch $task -> branch=$branch cli=$AGENT_CLI model=${model:-<default>} wt=$wt ==" | tee -a "$log"
  ( cd "$wt" && agent_exec "$prompt" "$model" ) >>"$log" 2>&1 &
  local pid=$!
  printf '%s\t%s\t%s\t%s\t%s\n' "$pid" "$branch" "$task" "$(date '+%Y-%m-%dT%H:%M:%S')" "$wt" >> "$REG"
  echo "dispatched: pid=$pid task=$task branch=$branch log=$log"
}

cmd_status(){
  printf '%-20s %-22s %-7s %-6s %-8s %s\n' TASK BRANCH PID STATE COMMITS LOG_TAIL
  local pid branch task started wt
  while IFS=$'\t' read -r pid branch task started wt; do
    [ -n "${pid:-}" ] || continue
    local state commits
    if kill -0 "$pid" 2>/dev/null; then state=RUN; else state=DONE; fi
    commits="$(git -C "$REPO" rev-list --count "master..$branch" 2>/dev/null || echo '?')"
    printf '%-20s %-22s %-7s %-6s %-8s %s\n' "$task" "$branch" "$pid" "$state" "$commits" "$(tail -1 "$RUNS/$task.log" 2>/dev/null | cut -c1-60)"
  done < "$REG"
}

cmd_logs(){ local task="${1:-}" n="${2:-40}"; [ -n "$task" ] || die "usage: logs <task> [n]"; tail -n "$n" "$RUNS/$task.log"; }

cmd_collect(){
  local task="${1:-}"; [ -n "$task" ] || die "usage: collect <task>"
  local branch; branch="$(awk -F'\t' -v t="$task" '$3==t{b=$2} END{print b}' "$REG")"
  [ -n "$branch" ] || die "no branch for task $task in registry"
  echo "== diff master..$branch =="; git -C "$REPO" --no-pager log --oneline "master..$branch"
  git -C "$REPO" --no-pager diff --stat "master..$branch"
  if have gh; then
    echo "opening PR for $branch ..."
    git -C "$REPO" push -u origin "$branch" 2>&1 | tail -2
    gh pr create --repo "$(git -C "$REPO" remote get-url origin)" --head "$branch" --base master \
      --title "$task ($branch)" --body "Worker output for task '$task'. Review before merge." 2>&1 | tail -3
  else
    echo "gh not installed — manual PR: push $branch and open it on GitHub."
  fi
}

cmd_stop(){
  local task="${1:-}" rm="${2:-}"; [ -n "$task" ] || die "usage: stop <task> [--rm-worktree]"
  local pid branch wt
  while IFS=$'\t' read -r pid branch t _ wt; do [ "$t" = "$task" ] && { kill "$pid" 2>/dev/null && echo "killed pid $pid"; break; }; done < "$REG"
  if [ "$rm" = "--rm-worktree" ] && [ -n "${wt:-}" ]; then
    git -C "$REPO" worktree remove --force "$wt" 2>&1 | tail -1
  fi
}

cmd_list(){
  echo "== task specs ($TASKS) =="; ls -1 "$TASKS"/*.md 2>/dev/null | sed 's#.*/##;s/\.md$//' || echo "(none)"
  echo "== registry ($REG) =="; column -t -s $'\t' "$REG" 2>/dev/null || cat "$REG"
}

case "${1:-}" in
  dispatch) shift; cmd_dispatch "$@";;
  status)   shift; cmd_status "$@";;
  logs)     shift; cmd_logs "$@";;
  collect)  shift; cmd_collect "$@";;
  stop)     shift; cmd_stop "$@";;
  list)     shift; cmd_list "$@";;
  *) sed -n '2,20p' "$0";;
esac
