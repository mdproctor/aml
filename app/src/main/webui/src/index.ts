// Import tab registry and panels
import { registerCaseTab } from './components/case-workbench/src/tab-registry.js';

// Import all panels (must be imported before registration)
import './panels/aml-investigation-overview.js';
import './panels/aml-findings-panel.js';
import './panels/aml-routing-panel.js';
import './panels/aml-compliance-panel.js';
import './panels/aml-audit-trail.js';

// Register all AML case tabs
registerCaseTab({
  id: 'overview',
  label: 'Overview',
  tagName: 'aml-investigation-overview',
  order: 0,
});

registerCaseTab({
  id: 'findings',
  label: 'Findings',
  tagName: 'aml-findings-panel',
  order: 10,
});

registerCaseTab({
  id: 'routing',
  label: 'Routing & Trust',
  tagName: 'aml-routing-panel',
  order: 20,
});

registerCaseTab({
  id: 'compliance',
  label: 'Compliance',
  tagName: 'aml-compliance-panel',
  order: 25,
});

registerCaseTab({
  id: 'audit',
  label: 'Audit',
  tagName: 'aml-audit-trail',
  order: 30,
});

// Import and define the root app element
import './aml-app.js';
