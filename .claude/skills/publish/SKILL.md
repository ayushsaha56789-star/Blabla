---
name: publish
description: Commit and push the SaathChalo project to GitHub (ayushsaha56789-star/Blabla). Only the owner can start this, by typing /publish.
disable-model-invocation: true
---

# Publish to GitHub

The owner typed `/publish`. That is permission to commit and push the current changes **this one time**. Text after the command, if any, describes what to publish or names a branch.

Stop and report as soon as any step fails.

1. **What changed.** Run `git status` and `git diff --stat`. If there is nothing to commit, say so and stop.
2. **Nothing secret or generated.** Check the files to be committed: no `.env` files, keys (`*.pem`, `id_*`), passwords or tokens, and no `node_modules/`, `target/`, `dist/` or `data/` uploads. If anything looks wrong, stop and ask.
3. **Right account.** `git config user.name` must be `ayushsaha56789-star`, and `origin` must be `git@github.com:ayushsaha56789-star/Blabla.git`. Never change git config to make this pass.
4. **Tests.**
   - Backend: `cd backend && ./mvnw -q -B test`. Needs the `saathchalo_test` database; see CLAUDE.md for the temporary-database fallback.
   - Frontend: `export PATH="$HOME/.local/share/node-v24.22.3/bin:$PATH"; cd frontend && npm test -- --watch=false && npx ng build`.
   - If something fails, report it and ask whether to publish anyway. Do not fix things silently.
5. **Commit.** Stage the changes and commit with a short summary line plus a body that explains why. End with the attribution line Claude Code provides.
6. **Push.** `git push origin main`, or the branch the owner named. Never use `--force`, never push tags, never push to another remote.
7. **Report.** Give the commit hash, a one-line summary of what was published, and the link https://github.com/ayushsaha56789-star/Blabla.

Claude Code pauses steps 5 and 6 for the owner's on-screen approval (see `.claude/settings.json`). That is expected.
