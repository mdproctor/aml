import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { emitPagesEvent } from '@casehubio/blocks-ui-core';
import type { ColumnDef } from '@casehubio/blocks-ui-data-table';
import '@casehubio/blocks-ui-data-table';

@customElement('case-list-pane')
export class CaseListPane extends LitElement {
  @property() endpoint = '';
  @property({ type: Array }) columns: ColumnDef<any>[] = [];
  @property({ attribute: false }) getRowKey: (row: any) => string = () => '';
  @property({ attribute: false }) getRowClass?: (row: any) => string;

  @state() private _rows: any[] = [];
  @state() private _loading = true;
  @state() private _error = '';
  @state() private _totalRows = 0;
  @state() private _currentPage = 0;
  @state() private _selectedKey = '';
  @state() private _filterText = '';

  static override styles = css`
    :host { display: flex; flex-direction: column; height: 100%; overflow: hidden; }
    .filter-bar {
      padding: var(--pages-space-2, 8px) var(--pages-space-3, 12px);
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .filter-bar input {
      width: 100%;
      padding: var(--pages-space-2, 8px);
      border: 1px solid var(--pages-neutral-5, #a3a3a3);
      border-radius: 4px;
      font-size: var(--pages-font-size-sm, 13px);
    }
    .table-container { flex: 1; overflow: hidden; }
    .error-banner {
      padding: var(--pages-space-3, 12px);
      background: var(--pages-danger-3, #fef2f2);
      color: var(--pages-danger-9, #991b1b);
      display: flex; justify-content: space-between; align-items: center;
    }
    .error-banner button {
      background: var(--pages-danger-9, #991b1b);
      color: white; border: none; padding: 4px 12px; border-radius: 4px; cursor: pointer;
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchData();
  }

  private async _fetchData(): Promise<void> {
    if (!this.endpoint) return;
    this._loading = true;
    this._error = '';
    try {
      const url = new URL(this.endpoint, window.location.origin);
      url.searchParams.set('page', String(this._currentPage));
      url.searchParams.set('pageSize', '50');
      const resp = await fetch(url.toString());
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      this._rows = data.items ?? data;
      this._totalRows = data.total ?? this._rows.length;
    } catch (e: any) {
      this._error = e.message ?? 'Failed to load';
    } finally {
      this._loading = false;
    }
  }

  private _onRowActivate(e: CustomEvent<{ row?: Record<string, unknown> }>): void {
    const row = e.detail?.row;
    if (!row) return;
    this._selectedKey = this.getRowKey(row);
    emitPagesEvent(this, 'case:selected', {
      caseId: this._selectedKey,
      caseData: row,
    });
  }

  private _onPageChange(e: CustomEvent<{ page?: number }>): void {
    this._currentPage = e.detail?.page ?? 0;
    this._fetchData();
  }

  override render() {
    if (this._error) {
      return html`
        <div class="error-banner">
          <span>${this._error}</span>
          <button @click=${this._fetchData}>Retry</button>
        </div>
      `;
    }
    return html`
      <div class="filter-bar">
        <input type="text" placeholder="Filter investigations..."
          .value=${this._filterText}
          @input=${(e: InputEvent) => { this._filterText = (e.target as HTMLInputElement).value; }}>
      </div>
      <div class="table-container">
        <pages-data-table
          .columns=${this.columns}
          .rows=${this._filteredRows}
          .getRowKey=${this.getRowKey}
          .getRowClass=${this.getRowClass ?? (() => '')}
          .loading=${this._loading}
          .totalRows=${this._totalRows}
          .currentPage=${this._currentPage}
          .selectedKeys=${this._selectedKey ? new Set([this._selectedKey]) : new Set()}
          selection="single"
          mode="paginated"
          emptyMessage="No investigations found"
          @row-activate=${this._onRowActivate}
          @page-change=${this._onPageChange}
        ></pages-data-table>
      </div>
    `;
  }

  private get _filteredRows(): any[] {
    if (!this._filterText) return this._rows;
    const lower = this._filterText.toLowerCase();
    return this._rows.filter((row: Record<string, unknown>) =>
      this.columns.some(col => {
        const val: unknown = col.getValue(row);
        if (val == null) return false;
        return String(val).toLowerCase().includes(lower);
      })
    );
  }

  refresh(): void {
    this._fetchData();
  }
}
