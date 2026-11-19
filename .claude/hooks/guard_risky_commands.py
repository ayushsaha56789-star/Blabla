#!/usr/bin/env python3
"""
SaathChalo safety guard: a Claude Code PreToolUse hook for the Bash tool.

Any shell command that could publish code, rewrite git history, change this
computer's configuration or destroy data is paused and shown to the owner
for an explicit on-screen approval ("ask"), whatever permission mode the
session is in. Every other command passes through untouched.

Claude Code sends the hook payload as JSON on stdin. Rules for Claude:
.claude/rules/git-and-github.md and .claude/rules/security.md.
"""
import json
import re
import sys

# Text up to the next shell separator, so one rule cannot match across `a && b`.
SEG = r"[^;&|\n]*"

RULES = [
    (rf"\bgit\b{SEG}\b(commit|push)\b",
     "creates a git commit or pushes code to GitHub"),
    (rf"\bgit\b{SEG}\b(reset\s+--hard|clean\b|rebase\b|filter-branch|filter-repo|update-ref|reflog\s+expire)",
     "rewrites git history or deletes uncommitted work"),
    (rf"\bgit\b{SEG}\b(checkout\s+--|restore\b|stash\s+(drop|clear)\b|branch\s+-D\b)",
     "throws away local changes or branches"),
    (rf"\bgit\b{SEG}\b(merge|tag)\b",
     "merges branches or creates tags"),
    (rf"\bgit\b{SEG}\bremote\s+(add|set-url|remove|rm|rename)\b",
     "changes where the code gets pushed"),
    (rf"\bgit\b{SEG}\bconfig\b{SEG}(--global|--system|--unset|--add|--replace-all|--edit\b)",
     "changes git settings"),
    (rf"\bgit\b{SEG}\bconfig(\s+--local)?\s+(user\.(name|email)|core\.sshcommand|remote\.\S+)\s+[^\s;&|]",
     "changes the git identity or remote settings"),
    (r"\bgh\s+(repo|pr|release|issue|gist|api|secret|auth|workflow)\b",
     "acts on GitHub through the GitHub CLI"),
    (r"\bsudo\b",
     "uses administrator (sudo) rights"),
    (rf"(>>?|\btee\b){SEG}\.(bashrc|bash_profile|profile|zshrc|zprofile|gitconfig)\b",
     "changes shell or git start-up files"),
    (rf"\bssh-keygen\b|(>>?|\btee\b|\bcp\b|\bmv\b|\brm\b|\bchmod\b){SEG}\.ssh/",
     "creates or changes SSH keys"),
    (rf"\bdrop\s+(database|schema|table)\b|\btruncate\b|\bflyway\b{SEG}\bclean\b",
     "deletes database data"),
    (rf"\b(curl|wget)\b{SEG}\|\s*(sudo\s+)?(ba|z|da)?sh\b",
     "runs a script downloaded from the internet"),
]


def ask(reason: str) -> None:
    json.dump(
        {
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "ask",
                "permissionDecisionReason": f"SaathChalo safety rule: this command {reason}. "
                "Approve only if you asked for it.",
            }
        },
        sys.stdout,
    )


def main() -> int:
    try:
        payload = json.load(sys.stdin)
        command = str((payload.get("tool_input") or {}).get("command") or "")
    except (ValueError, AttributeError):
        # If the command cannot be read it cannot be checked: let the owner decide.
        ask("could not be checked automatically")
        return 0

    reasons = [why for pattern, why in RULES if re.search(pattern, command, re.IGNORECASE)]
    if reasons:
        ask(" and ".join(dict.fromkeys(reasons)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
