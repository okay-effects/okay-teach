#!/bin/zsh
# A GPU JOB OF OKAY-TEACH, run when the Mac is free for it: no other MLX job (a JVM with libokm, python MLX), no JMH
# benchmark (another session's lane lock or its forked JVMs: our load would spoil its numbers), and under the shared
# GPU lock ~/work/my/okay-inlay-private/gpu.lock (mkdir is atomic; only a lock made here is removed).
#   scripts/gpu.zsh <log> <command...>      (waits are written to runs/wait.log)
L=/Users/sergiy/work/my/okay-inlay-private/gpu.lock
J=${TMPDIR:-/tmp}/okay-jmh-lane.lock
W=runs/wait.log
mkdir -p runs
log=$1; shift
held=0
unlock() { if [ $held = 1 ]; then rmdir $L; held=0; echo "$(date '+%H:%M:%S') lock released" >> $W; fi }
trap unlock EXIT
trap 'unlock; exit 1' INT TERM HUP
busy() {
  [ -d $L ] || [ -d $J ] || pgrep -f "[l]ibokm" >/dev/null || pgrep -f "python.*[m]lx" >/dev/null ||
    pgrep -f "[j]mh.runner" >/dev/null || pgrep -f "[C]ompileCommandFile" >/dev/null
}
echo "$(date '+%H:%M:%S') $log: waiting for a free GPU" >> $W
while true; do
  while busy; do sleep 20; done
  if mkdir $L 2>/dev/null; then held=1; break; fi
done
echo "$(date '+%H:%M:%S') $log: free, lock taken, starting" >> $W
"$@" > $log 2>&1
echo "$(date '+%H:%M:%S') $log: exited $?" >> $W
