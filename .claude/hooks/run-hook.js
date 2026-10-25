#!/usr/bin/env node
// run-hook.js <name> — one hook command for every OS (settings.local.json):
//   node "$CLAUDE_PROJECT_DIR/.claude/hooks/run-hook.js" <name>
// Windows runs <name>.ps1 through Windows PowerShell, exactly as before; Linux/macOS run <name>.sh
// (bash) or, when there is none, <name>.py (python3). stdin, stdout, stderr and the exit code pass
// straight through, so each script keeps the Claude Code hook contract it already implements.
// A missing script or interpreter exits 0: a hook must never trap the session.
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
if (!name || !/^[\w-]+$/.test(name)) process.exit(0);
const base = path.join(__dirname, name);

let cmd;
let args;
if (process.platform === 'win32') {
  cmd = 'powershell';
  args = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', `${base}.ps1`];
  if (!fs.existsSync(`${base}.ps1`)) process.exit(0);
} else if (fs.existsSync(`${base}.sh`)) {
  cmd = 'bash';
  args = [`${base}.sh`];
} else if (fs.existsSync(`${base}.py`)) {
  cmd = 'python3';
  args = [`${base}.py`];
} else {
  process.exit(0);
}

const r = spawnSync(cmd, args, { stdio: 'inherit' });
process.exit(r.error ? 0 : (r.status ?? 0));
