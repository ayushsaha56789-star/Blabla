# Working agreement with the owner

## Before starting

- For a new phase, a big feature, a new tool or paid service, or anything that touches the owner's accounts: explain what you will build, how, roughly how long it takes and what the owner has to do. Then wait for "start" or "yes".
- Small fixes the owner clearly asked for can start straight away.
- Ask only when the decision really is the owner's (names, paid services, design direction). Otherwise pick a sensible default and say which one you picked.

## While working

- **Useful first** (the owner's main motto): members must feel the app is useful to them. Give each phase something members or the owner can use straight away. Keep legal and paperwork features light and switchable until launch, and never weaken security.
- Build a web application (responsive website), not a native app, unless the owner says otherwise.
- Keep the SaathChalo brand. Never copy BlaBlaCar's name, logo, text or screens.
- The UI should be modern, simple and friendly: works well on phones, light and dark mode, clear wording, big tap targets, helpful error messages.
- Follow the phase plan in `README.md`, and update its status table when a phase is finished.

## Avoiding needless approval prompts

- Write and change files with the editor tools (Write / Edit), not with shell commands that contain the file's text (heredocs). The safety guard reads shell commands, and code inside them (for example the word `TRUNCATE` in a test) triggers prompts the owner then has to click through.

## Before saying "done"

- Run the backend tests, the frontend tests and a production build.
- For UI changes, check the real pages in Chrome at laptop and phone sizes, in light and dark mode. Use Puppeteer with `/usr/bin/google-chrome`, installed in the scratchpad, never in the project.
- Stop any servers or temporary databases you started.
- In the final message, say what changed, how it was checked (with numbers), what the owner needs to do, and offer to commit and push. Do not commit or push.

## When something is blocked

- If a command is denied or paused, or needs admin rights: stop, explain in one or two sentences why it is needed, and give the owner the exact command. Never look for a way around the block.
