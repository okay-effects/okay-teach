#!/bin/zsh
# THE THREE DOORS INSTALLED (SPRINT T14): the command `okay-teach` linked into ~/.local/bin, the skill linked into
# ~/.agents/skills and ~/.claude/skills (links, not copies: an update of this checkout updates them), and the MCP
# server's registration PRINTED — not done: adding a server to an agent's configuration is the owner's choice.
set -e
root=${0:A:h:h}
mkdir -p ~/.local/bin ~/.agents/skills ~/.claude/skills
ln -sfn $root/bin/okay-teach ~/.local/bin/okay-teach
for d in ~/.agents/skills ~/.claude/skills; do ln -sfn $root/skills/teach-a-model $d/teach-a-model; done
echo "okay-teach: ~/.local/bin/okay-teach, the skill teach-a-model in ~/.agents/skills and ~/.claude/skills"
echo "The MCP server, to add yourself:"
echo "  Claude Code:  claude mcp add okay-teach -- $root/bin/okay-teach mcp"
echo "  opencode:     \"mcp\": {\"okay-teach\": {\"type\": \"local\", \"command\": [\"$root/bin/okay-teach\", \"mcp\"]}}"
