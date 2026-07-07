import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { ColumnDef } from '@casehubio/blocks-ui-data-table';
import './components/case-workbench/src/index.js';
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

@customElement('aml-app')
export class AmlApp extends LitElement {
  @state() private _activeView: ViewId = 'investigations';

  private _investigationColumns: ColumnDef<Investigation>[] = [
    {
      key: 'caseId',
      header: 'Case ID',
      getValue: (row) => row.caseId.split('-')[0],
    },
    {
      key: 'status',
      header: 'Status',
      getValue: (row) => row.status,
    },
    {
      key: 'flagReason',
      header: 'Flag Reason',
      getValue: (row) => row.flagReason,
    },
    {
      key: 'amount',
      header: 'Amount',
      getValue: (row) => `${row.amount.toLocaleString()} ${row.currency}`,
    },
    {
      key: 'createdAt',
      header: 'Created',
      getValue: (row) => new Date(row.createdAt).toLocaleDateString(),
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

  private _getRowClass = (row: Investigation): string => {
    if (row.status === 'failed' || row.status === 'cancelled') return 'row-high-risk';
    if (row.status === 'suspended') return 'row-medium-risk';
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
          <case-workbench
            endpoint="/api/investigations"
            title="AML Investigations"
            .columns=${this._investigationColumns}
            .getRowKey=${(row: Investigation) => row.caseId}
            .getRowClass=${this._getRowClass}
          ></case-workbench>
        `;
      case 'compliance':
        return html`
          <work-item-inbox
            endpoint="/api/work-items"
            title="Compliance Review Queue"
          ></work-item-inbox>
        `;
      case 'operations':
        return html`<aml-operations-view></aml-operations-view>`;
    }
  }
}
