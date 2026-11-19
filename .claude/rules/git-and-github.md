# Git and GitHub rules

## Permission to commit or push

- Commit and push **only** when the owner asks for it in the current conversation. These count: "commit this", "push it", "publish to GitHub", `/publish`.
- These do **not** count: "start", "continue", "go ahead with phase 2", "make it work", approving a plan, or an earlier general wish to publish in this account.
- One request covers one commit and push. Next time, ask again.
- If you are not sure whether something is permission, ask.
- Why this rule exists: on 2026-11-19 Phase 1 was committed and pushed without being asked. That must not happen again.

## Never, unless the owner asks for exactly that action

- Force-push, or rewrite history that is already on GitHub (amend, rebase, `reset --hard`).
- Delete branches or tags, or create tags, releases, pull requests or issues.
- Change remotes, or push to any other repository, account or branch than the one the owner named.
- Change git config. This repo already has its own identity (`ayushsaha56789-star`) and SSH key setting. Never touch the global git config: it belongs to a different GitHub account.

## Fine without asking (local and read-only)

`git status`, `git diff`, `git log`, `git show`, listing branches, `git fetch`, and staging with `git add`.

## When the owner asks you to commit or push

1. Run `git status` and `git diff --stat` and tell the owner what will be included.
2. Make sure nothing secret or generated is staged: no `.env` files, keys (`*.pem`, `id_*`), passwords, `node_modules/`, `target/`, `dist/`, or `data/` uploads.
3. Run the backend tests, the frontend tests and a production build. If anything fails, stop and report it. Only continue if the owner says so knowing about the failure.
4. Check the identity: `git config user.name` is `ayushsaha56789-star` and `origin` is `git@github.com:ayushsaha56789-star/Blabla.git`.
5. Commit with a short summary line and a body that explains why. End the message with the attribution line Claude Code provides.
6. Push to `origin main`, or to the branch the owner named. Report the commit hash and the GitHub link.

The safety hook pauses `git commit` and `git push` for the owner's on-screen approval. That is expected; never try to avoid it.
