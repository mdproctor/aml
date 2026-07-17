import { build, context } from "esbuild";
import { cpSync, existsSync, readFileSync } from "fs";
import { resolve, dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const isWatch = process.argv.includes("--watch");

// Plugin to resolve all @casehubio/* imports via AML's node_modules.
// Dependencies (blocks-ui, pages) import each other by bare specifier,
// but esbuild resolves relative to the importing file — which is inside
// a file: linked package, not AML's node_modules tree. This plugin
// redirects all @casehubio/* imports to AML's node_modules.
const casehubResolvePlugin = {
  name: 'casehub-resolve',
  setup(build) {
    build.onResolve({ filter: /^@casehubio\// }, args => {
      const parts = args.path.split('/');
      const scope = parts[0]; // @casehubio
      const pkgName = parts[1]; // pages-table, blocks-ui-core, etc.
      const subpath = parts.slice(2).join('/'); // optional: dist/types.js

      // Try node_modules/<scope>/<pkg> first
      const pkgDir = join(__dirname, 'node_modules', scope, pkgName);
      if (!existsSync(pkgDir)) return null;

      if (subpath) {
        // Subpath import: @casehubio/pages-data/dist/dataset/types.js
        const fullPath = join(pkgDir, subpath);
        if (existsSync(fullPath)) return { path: fullPath };
        // Try without dist prefix
        const distPath = join(pkgDir, 'dist', subpath);
        if (existsSync(distPath)) return { path: distPath };
      }

      // Bare import: @casehubio/pages-table → resolve via package.json main
      const pkgJson = join(pkgDir, 'package.json');
      if (existsSync(pkgJson)) {
        const pkg = JSON.parse(readFileSync(pkgJson, 'utf8'));
        const main = pkg.main || 'dist/index.js';
        const mainPath = join(pkgDir, main);
        if (existsSync(mainPath)) return { path: mainPath };
      }

      return null;
    });
  },
};

const sharedOptions = {
  bundle: true,
  format: "esm",
  target: "es2020",
  plugins: [casehubResolvePlugin],
  resolveExtensions: ['.ts', '.js', '.mjs'],
};

const mainOptions = {
  ...sharedOptions,
  entryPoints: ["src/index.ts"],
  outfile: "dist/app.js",
  minify: !isWatch,
  sourcemap: isWatch,
};

const showcaseOptions = {
  ...sharedOptions,
  entryPoints: ["src/showcase/index.ts"],
  outfile: "dist/showcase.js",
  minify: false,
  sourcemap: true,
};

function copyAssets() {
  cpSync("index.html", "dist/index.html");
  cpSync("showcase.html", "dist/showcase.html");
}

if (isWatch) {
  const mainCtx = await context(mainOptions);
  const showcaseCtx = await context(showcaseOptions);
  await mainCtx.watch();
  await showcaseCtx.watch();
  copyAssets();
  console.log("Watching for changes...");
} else {
  await build(mainOptions);
  await build(showcaseOptions);
  copyAssets();
}
