# CloudStream Poster Downloader (prototype)

A tiny independent CloudStream extension based on the supplied plugin template.
It registers a **تنزيل البوستر** action using CloudStream's `VideoClickAction` API and attempts to discover an image URL from the selected video object's public/private string fields and getters. If it finds one, it saves the image to the Android gallery using MediaStore, under `Pictures/CloudStream Posters` on Android 10+.

## Build using GitHub Actions

1. Upload the contents of this repository to a new GitHub repository (keep `.github/workflows/build.yml`).
2. Ensure GitHub Actions is enabled, then open **Actions** and run **Build CloudStream extension** (or push a commit).
3. Open the successful workflow run and download the artifact named `CloudStream-Poster-Downloader`.
4. Inside the artifact, use the generated `.cs3` file. Install it in CloudStream using the app's extension/plugin installation method supported by your build.

## Important usage note

The extension registers through CloudStream's video-action API. Thus the action appears in the relevant video/source action menu, not as a guaranteed button on the movie detail page. Whether a poster can be found depends on whether that CloudStream version passes poster metadata into the action's video object. If it cannot find the URL, it shows a message rather than crashing.

This is a prototype built against the API version declared by the supplied template. The build workflow compiles it on GitHub; actual behavior must be tested in your installed CloudStream version.
