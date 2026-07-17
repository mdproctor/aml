// Prevent duplicate custom element registration errors.
// esbuild bundles may include the same Lit component from multiple
// import paths (blocks-ui + pages), causing double define() calls.
const origDefine = customElements.define.bind(customElements);
customElements.define = function(name: string, ctor: CustomElementConstructor, options?: ElementDefinitionOptions) {
  if (!customElements.get(name)) {
    origDefine(name, ctor, options);
  }
};
