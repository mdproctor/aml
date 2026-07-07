import { build, context } from "esbuild";
import { cpSync, existsSync } from "fs";
import { resolve, dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const isWatch = process.argv.includes("--watch");

// Plugin to resolve subpath imports from @casehubio packages
const subpathResolvePlugin = {
  name: 'subpath-resolve',
  setup(build) {
    // Match imports like @casehubio/pages-data/sse/sse-manager.js
    build.onResolve({ filter: /^@casehubio\/[^/]+\/.+\.js$/ }, args => {
      const parts = args.path.split('/');
      const scope = parts[0]; // @casehubio
      const pkgName = parts[1]; // pages-data
      const subpath = parts.slice(2).join('/'); // sse/sse-manager.js

      const nodeModulesPath = join(__dirname, 'node_modules', scope, pkgName, 'dist', subpath);
      if (existsSync(nodeModulesPath)) {
        return { path: nodeModulesPath };
      }
      return null;
    });
  },
};

const mainOptions = {
  entryPoints: ["src/index.ts"],
  bundle: true,
  outfile: "dist/app.js",
  format: "esm",
  target: "es2020",
  minify: !isWatch,
  sourcemap: isWatch,
  plugins: [subpathResolvePlugin],
  resolveExtensions: ['.ts', '.js', '.mjs'],
};

function copyAssets() {
  cpSync("index.html", "dist/index.html");
}

if (isWatch) {
  const mainCtx = await context(mainOptions);
  await mainCtx.watch();
  copyAssets();
  console.log("Watching for changes...");
} else {
  await build(mainOptions);
  copyAssets();
}
