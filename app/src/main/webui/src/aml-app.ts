import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import type { TypedRow } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TabDefinition } from '@casehubio/blocks-ui-detail-pane';
import '@casehubio/blocks-ui-split-workbench';
import '@casehubio/blocks-ui-list-pane';
import '@casehubio/blocks-ui-detail-pane';
import '@casehubio/blocks-ui-work-item-inbox';
import './views/operations.js';
import './panels/index.js';

type ViewId = 'investigations' | 'compliance' | 'operations';

interface Investigation {
  caseId: string;
  status: string;
  flagReason: string;
  amount: number;
  currency: string;
  createdAt: string;
}

const investigationTabs: TabDefinition[] = [
  { id: 'overview', label: 'Overview', tagName: 'aml-investigation-overview', order: 0 },
  { id: 'findings', label: 'Findings', tagName: 'aml-findings-panel', order: 10 },
  { id: 'routing', label: 'Routing & Trust', tagName: 'aml-routing-panel', order: 20 },
  { id: 'compliance', label: 'Compliance', tagName: 'aml-compliance-panel', order: 25 },
  { id: 'audit', label: 'Audit', tagName: 'aml-audit-trail', order: 30 },
];

@customElement('aml-app')
export class AmlApp extends LitElement {
  @state() private _activeView: ViewId = 'investigations';

  private _investigationColumns: TableColumnConfig[] = [
    {
      id: 'caseId',
      label: 'Case ID',
      sortable: true,
    },
    {
      id: 'status',
      label: 'Status',
      sortable: true,
    },
    {
      id: 'flagReason',
      label: 'Flag Reason',
      sortable: true,
    },
    {
      id: 'amount',
      label: 'Amount',
      sortable: true,
    },
    {
      id: 'createdAt',
      label: 'Created',
      sortable: true,
    },
  ];

  static override styles = css`
    :host {
      display: flex;
      height: 100vh;
      width: 100%;
      font-family: var(--pages-font-family, system-ui);
      background: var(--pages-neutral-1, #ffffff);
    }

    .sidebar {
      width: 240px;
      background: var(--pages-neutral-2, #f5f5f5);
      border-right: 1px solid var(--pages-neutral-4, #d4d4d4);
      display: flex;
      flex-direction: column;
    }

    .sidebar-header {
      padding: var(--pages-space-4, 16px);
      font-size: var(--pages-font-size-lg, 16px);
      font-weight: 700;
      color: var(--pages-neutral-11, #0a0a0a);
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }

    .nav-item {
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px);
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 500;
      color: var(--pages-neutral-8, #404040);
      border-left: 3px solid transparent;
      user-select: none;
    }

    .nav-item:hover {
      background: var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .nav-item.active {
      background: var(--pages-accent-1, #eff6ff);
      color: var(--pages-accent-9, #3b82f6);
      border-left-color: var(--pages-accent-9, #3b82f6);
    }

    .main-content {
      flex: 1;
      overflow: hidden;
    }
  `;

  private _switchView(view: ViewId): void {
    if (window.location.hash !== `#${view}`) {
      window.location.hash = view;
    }
    this._activeView = view;
  }

  override connectedCallback(): void {
    super.connectedCallback();
    const hash = window.location.hash.slice(1);
    if (hash === 'investigations' || hash === 'compliance' || hash === 'operations') {
      this._activeView = hash;
    }
    window.addEventListener('hashchange', this._handleHashChange);
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    window.removeEventListener('hashchange', this._handleHashChange);
  }

  private _handleHashChange = (): void => {
    const hash = window.location.hash.slice(1);
    if (hash === 'investigations' || hash === 'compliance' || hash === 'operations') {
      this._activeView = hash;
    }
  };

  private _getRowClass = (row: TypedRow): string => {
    const status = row.text('status');
    if (status === 'failed' || status === 'cancelled') return 'row-high-risk';
    if (status === 'suspended') return 'row-medium-risk';
    return '';
  };

  override render() {
    return html`
      <div class="sidebar">
        <div class="sidebar-header">AML Workbench</div>
        ${this._renderNavItem('investigations', 'Investigations')}
        ${this._renderNavItem('compliance', 'Compliance')}
        ${this._renderNavItem('operations', 'Operations')}
      </div>
      <div class="main-content">
        ${this._renderActiveView()}
      </div>
    `;
  }

  private _renderNavItem(id: ViewId, label: string) {
    return html`
      <div
        class="nav-item ${this._activeView === id ? 'active' : ''}"
        @click=${() => this._switchView(id)}
      >
        ${label}
      </div>
    `;
  }

  private _renderActiveView() {
    switch (this._activeView) {
      case 'investigations':
        return html`
          <split-workbench selection-topic="case" title="AML Investigations">
            <list-pane slot="list"
              selection-topic="case"
              endpoint="/api/investigations"
              .columnConfig=${this._investigationColumns}
              .getRowKey=${(row: TypedRow) => row.text('caseId')}
              .getRowClass=${this._getRowClass}>
            </list-pane>
            <detail-pane slot="detail"
              selection-topic="case"
              .tabs=${investigationTabs}
              empty-message="Select an investigation to view details">
            </detail-pane>
          </split-workbench>
        `;
      case 'compliance':
        return html`
          <work-item-inbox
            endpoint=""
            title="Compliance Review Queue"
          ></work-item-inbox>
        `;
      case 'operations':
        return html`<aml-operations-view></aml-operations-view>`;
    }
  }
}
